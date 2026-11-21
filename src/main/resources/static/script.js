const roomGrid = document.querySelector("#room-grid");
const roomResults = document.querySelector("#room-results");
const roomsEmpty = document.querySelector("#rooms-empty");
const roomsEmptyCopy = document.querySelector("#rooms-empty-copy");
const searchForm = document.querySelector("#search-form");
const searchCheckIn = document.querySelector("#search-check-in");
const searchCheckOut = document.querySelector("#search-check-out");
const resetSearch = document.querySelector("#reset-search");
const bookingDialog = document.querySelector("#booking-dialog");
const paymentDialog = document.querySelector("#payment-dialog");
const bookingForm = document.querySelector("#booking-form");
const bookingError = document.querySelector("#booking-error");
const bookingCheckIn = document.querySelector("#booking-check-in");
const bookingCheckOut = document.querySelector("#booking-check-out");
const bookingList = document.querySelector("#booking-list");
const lookupForm = document.querySelector("#lookup-form");
const toast = document.querySelector("#toast");

const roomImages = [
	"https://images.unsplash.com/photo-1611892440504-42a792e24d32?auto=format&fit=crop&w=1000&q=80",
	"https://images.unsplash.com/photo-1618221195710-dd6b41faaea6?auto=format&fit=crop&w=1000&q=80",
	"https://images.unsplash.com/photo-1590490360182-c33d57733427?auto=format&fit=crop&w=1000&q=80"
];

let activeDates = null;
let toastTimer;
let pendingRoom = null;
let pendingPaymentOrder = null;
let razorpayScriptPromise;

function localDateString(date) {
	const year = date.getFullYear();
	const month = String(date.getMonth() + 1).padStart(2, "0");
	const day = String(date.getDate()).padStart(2, "0");
	return `${year}-${month}-${day}`;
}

function setDateConstraints(checkIn, checkOut) {
	const today = localDateString(new Date());
	checkIn.min = today;
	if (checkIn.value && checkIn.value < today) checkIn.value = today;
	const minimumCheckOut = new Date(`${checkIn.value || today}T00:00:00`);
	minimumCheckOut.setDate(minimumCheckOut.getDate() + 1);
	checkOut.min = localDateString(minimumCheckOut);
	if (checkOut.value && checkOut.value < checkOut.min) {
		checkOut.value = checkOut.min;
	}
}

function setInitialDates() {
	const today = new Date();
	const tomorrow = new Date(today);
	tomorrow.setDate(tomorrow.getDate() + 1);
	searchCheckIn.value = localDateString(today);
	searchCheckOut.value = localDateString(tomorrow);
	setDateConstraints(searchCheckIn, searchCheckOut);
}

function formatCurrency(value) {
	return new Intl.NumberFormat("en-IN", {
		style: "currency",
		currency: "INR",
		maximumFractionDigits: 0
	}).format(Number(value) || 0);
}

function formatDate(value) {
	if (!value) return "";
	return new Intl.DateTimeFormat("en-IN", {
		day: "numeric",
		month: "short",
		year: "numeric"
	}).format(new Date(`${value}T00:00:00`));
}

function safeImageUrl(value, index) {
	if (typeof value === "string" && value.trim()) {
		try {
			const url = new URL(value, window.location.origin);
			if (url.protocol === "https:" || url.protocol === "http:") return url.href;
		} catch {
			// Use a curated fallback for invalid image URLs.
		}
	}
	return roomImages[index % roomImages.length];
}

function request(path, options = {}) {
	return window.HotelAuth.request(path, options);
}

function showToast(message) {
	window.clearTimeout(toastTimer);
	toast.textContent = message;
	toast.classList.add("visible");
	toastTimer = window.setTimeout(() => toast.classList.remove("visible"), 4200);
}

function showPaymentError(message) {
	const error = document.querySelector("#payment-error");
	error.textContent = message;
	error.hidden = false;
}

async function loadRazorpayCheckout() {
	if (window.Razorpay) return;
	if (!razorpayScriptPromise) {
		razorpayScriptPromise = new Promise((resolve, reject) => {
			const script = document.createElement("script");
			script.src = "https://checkout.razorpay.com/v1/checkout.js";
			script.onload = resolve;
			script.onerror = () => reject(new Error("Unable to load Razorpay checkout."));
			document.head.append(script);
		});
	}
	return razorpayScriptPromise;
}

async function verifyPayment(verification) {
	const result = await request("/payment/verify", {
		method: "POST",
		body: JSON.stringify(verification)
	});
	pendingPaymentOrder = null;
	paymentDialog.close();
	bookingDialog.close();
	bookingForm.reset();
	showToast(`Payment verified. Booking #${result.bookingId} is confirmed.`);
	if (activeDates) await loadRooms(activeDates);
}

async function payForBooking() {
	if (!pendingPaymentOrder) return;
	const button = document.querySelector("#pay-now");
	const order = pendingPaymentOrder;
	button.disabled = true;
	document.querySelector("#payment-error").hidden = true;
	try {
		if (order.testMode) {
			await verifyPayment({ orderId: order.orderId, simulated: true });
			return;
		}

		await loadRazorpayCheckout();
		const user = window.HotelAuth.getUser();
		const checkout = new window.Razorpay({
			key: order.keyId,
			amount: order.amount,
			currency: order.currency,
			name: "Haven House",
			description: document.querySelector("#payment-room").textContent,
			order_id: order.orderId,
			prefill: {
				name: user?.fullName || "",
				email: user?.email || "",
				contact: bookingForm.elements.guestPhone.value
			},
			theme: { color: "#31483e" },
			handler: async (response) => {
				try {
					await verifyPayment({
						orderId: response.razorpay_order_id,
						paymentId: response.razorpay_payment_id,
						signature: response.razorpay_signature,
						simulated: false
					});
				} catch (error) {
					showPaymentError(error.message || "Payment verification failed.");
					button.disabled = false;
				}
			}
		});
		checkout.on("payment.failed", (event) => {
			showPaymentError(event.error?.description || "Payment failed. Please try again.");
			button.disabled = false;
		});
		checkout.open();
	} catch (error) {
		showPaymentError(error.message || "Unable to start checkout.");
		button.disabled = false;
	}
}

function setRoomMessage(message, isError = false) {
	roomResults.textContent = message;
	roomResults.classList.toggle("error", isError);
}

function renderRooms(rooms) {
	roomGrid.replaceChildren();
	roomsEmpty.hidden = rooms.length > 0;
	if (rooms.length === 0) {
		roomsEmptyCopy.textContent = activeDates
			? "No rooms are available for those dates. Try another date range."
			: "There are no rooms to show right now. Please check back soon.";
	}

	for (const [index, room] of rooms.entries()) {
		const card = document.createElement("article");
		card.className = "room-card";

		const imageWrap = document.createElement("div");
		imageWrap.className = "room-image-wrap";
		const image = document.createElement("img");
		image.className = "room-image";
		image.src = safeImageUrl(room.imageUrl, index);
		image.alt = `${room.roomType || "Hotel"} room`;
		image.loading = "lazy";
		imageWrap.append(image);

		const badge = document.createElement("span");
		badge.className = "room-type-badge";
		badge.textContent = room.roomType || "Guest room";
		imageWrap.append(badge);

		const content = document.createElement("div");
		content.className = "room-content";
		const number = document.createElement("p");
		number.className = "room-number";
		number.textContent = room.roomNumber ? `ROOM ${room.roomNumber}` : "YOUR PRIVATE RETREAT";
		const title = document.createElement("h3");
		title.className = "room-name";
		title.textContent = room.roomType || "Guest room";
		const description = document.createElement("p");
		description.className = "room-description";
		description.textContent = room.description || "A considered space for a comfortable and restful stay.";

		const footer = document.createElement("div");
		footer.className = "room-card-bottom";
		const price = document.createElement("span");
		price.className = "room-price";
		price.textContent = formatCurrency(room.pricePerNight);
		const perNight = document.createElement("small");
		perNight.textContent = "per night";
		price.append(perNight);

		const bookButton = document.createElement("button");
		bookButton.className = "button button-dark room-book-button";
		bookButton.type = "button";
		bookButton.textContent = "Book this room";
		bookButton.addEventListener("click", () => openBooking(room));
		footer.append(price, bookButton);
		content.append(number, title, description, footer);
		card.append(imageWrap, content);
		roomGrid.append(card);
	}
}

async function loadRooms(dates = null) {
	activeDates = dates;
	resetSearch.hidden = !dates;
	roomsEmpty.hidden = true;
	setRoomMessage(dates ? "Checking availability…" : "Loading rooms…");
	try {
		const query = dates
			? `?checkInDate=${encodeURIComponent(dates.checkInDate)}&checkOutDate=${encodeURIComponent(dates.checkOutDate)}`
			: "";
		const endpoint = dates ? `/rooms/available${query}` : "/rooms";
		const rooms = await request(endpoint);
		renderRooms(rooms);
		setRoomMessage(`${rooms.length} ${rooms.length === 1 ? "room" : "rooms"} ${dates ? "available" : "to discover"}`);
	} catch (error) {
		renderRooms([]);
		setRoomMessage(error.message || "We couldn't load rooms. Please try again.", true);
		roomsEmpty.hidden = false;
		roomsEmptyCopy.textContent = "Please try again in a moment.";
	}
}

function openBooking(room) {
	const user = window.HotelAuth.getUser();
	if (!user) {
		pendingRoom = room;
		window.HotelAuth.open("login");
		showToast("Log in or create an account to book a stay.");
		return;
	}
	bookingError.hidden = true;
	bookingError.textContent = "";
	bookingForm.reset();
	document.querySelector("#booking-room-id").value = room.id;
	bookingForm.elements.guestEmail.value = user.email;
	bookingForm.elements.guestEmail.readOnly = true;
	document.querySelector("#dialog-room").textContent =
		`${room.roomType || "Guest room"}${room.roomNumber ? ` · Room ${room.roomNumber}` : ""} · ${formatCurrency(room.pricePerNight)} per night`;
	bookingCheckIn.value = activeDates?.checkInDate || searchCheckIn.value;
	bookingCheckOut.value = activeDates?.checkOutDate || searchCheckOut.value;
	setDateConstraints(bookingCheckIn, bookingCheckOut);
	bookingDialog.showModal();
}

function renderBookings(bookings) {
	bookingList.replaceChildren();
	if (!bookings.length) {
		const message = document.createElement("p");
		message.className = "lookup-message";
		message.textContent = "No bookings found for that email address.";
		bookingList.append(message);
		return;
	}

	for (const booking of bookings) {
		const row = document.createElement("article");
		row.className = "booking-row";
		const info = document.createElement("div");
		info.className = "booking-info";
		const title = document.createElement("h3");
		title.className = "booking-room-name";
		title.textContent = booking.room?.roomType || "Hotel room";
		const meta = document.createElement("p");
		meta.className = "booking-meta";
		const roomNumber = booking.room?.roomNumber ? ` · Room ${booking.room.roomNumber}` : "";
		meta.textContent = `${formatDate(booking.checkInDate)} – ${formatDate(booking.checkOutDate)}${roomNumber}`;
		const status = document.createElement("span");
		const isCancelled = String(booking.status || "").toLowerCase() === "cancelled";
		status.className = `status-pill${isCancelled ? " cancelled" : ""}`;
		status.textContent = booking.status || "Pending";
		info.append(title, meta, status);

		const side = document.createElement("div");
		side.className = "booking-side";
		const total = document.createElement("span");
		total.className = "booking-total";
		total.textContent = formatCurrency(booking.totalPrice);
		side.append(total);
		if (!isCancelled) {
			const cancelButton = document.createElement("button");
			cancelButton.className = "cancel-button";
			cancelButton.type = "button";
			cancelButton.textContent = "Cancel booking";
			cancelButton.addEventListener("click", () => cancelBooking(booking.id, cancelButton));
			side.append(cancelButton);
		}
		row.append(info, side);
		bookingList.append(row);
	}
}

async function findBookings(guestEmail) {
	const user = window.HotelAuth.getUser();
	if (!user) {
		window.HotelAuth.open("login");
		return;
	}
	if (guestEmail.toLowerCase() !== user.email.toLowerCase()) {
		bookingList.replaceChildren();
		const message = document.createElement("p");
		message.className = "lookup-message error";
		message.textContent = "Sign in with the email address used for this booking.";
		bookingList.append(message);
		return;
	}
	bookingList.replaceChildren();
	const loading = document.createElement("p");
	loading.className = "lookup-message";
	loading.textContent = "Looking up your bookings…";
	bookingList.append(loading);
	try {
		const bookings = await request(`/bookings/mine?guestEmail=${encodeURIComponent(guestEmail)}`);
		renderBookings(bookings);
	} catch (error) {
		bookingList.replaceChildren();
		const message = document.createElement("p");
		message.className = "lookup-message error";
		message.textContent = error.message || "We couldn't retrieve your bookings.";
		bookingList.append(message);
	}
}

async function cancelBooking(bookingId, button) {
	if (!window.confirm("Cancel this booking?")) return;
	button.disabled = true;
	try {
		await request(`/bookings/${encodeURIComponent(bookingId)}/cancel`, { method: "PATCH" });
		showToast("Your booking has been cancelled.");
		await findBookings(document.querySelector("#lookup-email").value.trim());
		if (activeDates) await loadRooms(activeDates);
	} catch (error) {
		button.disabled = false;
		showToast(error.message || "We couldn't cancel this booking. Please try again.");
	}
}

searchCheckIn.addEventListener("change", () => setDateConstraints(searchCheckIn, searchCheckOut));
bookingCheckIn.addEventListener("change", () => setDateConstraints(bookingCheckIn, bookingCheckOut));

searchForm.addEventListener("submit", async (event) => {
	event.preventDefault();
	if (!searchForm.reportValidity()) return;
	const dates = {
		checkInDate: searchCheckIn.value,
		checkOutDate: searchCheckOut.value
	};
	if (dates.checkOutDate <= dates.checkInDate) {
		searchCheckOut.setCustomValidity("Check-out must be after check-in.");
		searchCheckOut.reportValidity();
		searchCheckOut.setCustomValidity("");
		return;
	}
	await loadRooms(dates);
	document.querySelector("#rooms").scrollIntoView({ behavior: "smooth" });
});

resetSearch.addEventListener("click", () => loadRooms());

document.querySelector("#close-dialog").addEventListener("click", () => bookingDialog.close());
bookingDialog.addEventListener("click", (event) => {
	if (event.target === bookingDialog) bookingDialog.close();
});
document.querySelector("#close-payment").addEventListener("click", () => paymentDialog.close());
paymentDialog.addEventListener("click", (event) => {
	if (event.target === paymentDialog) paymentDialog.close();
});
paymentDialog.addEventListener("close", async () => {
	const order = pendingPaymentOrder;
	if (!order) return;
	pendingPaymentOrder = null;
	try {
		await request(`/payment/cancel-order?orderId=${encodeURIComponent(order.orderId)}`, { method: "POST" });
		if (activeDates) await loadRooms(activeDates);
	} catch (error) {
		showToast(error.message || "Unable to release the pending checkout.");
	}
});
document.querySelector("#pay-now").addEventListener("click", payForBooking);

bookingForm.addEventListener("submit", async (event) => {
	event.preventDefault();
	if (!bookingForm.reportValidity()) return;
	const user = window.HotelAuth.getUser();
	if (!user) {
		window.HotelAuth.open("login");
		return;
	}
	const form = new FormData(bookingForm);
	const requestBody = {
		roomId: Number(form.get("roomId")),
		guestName: String(form.get("guestName")).trim(),
		guestEmail: user.email,
		guestPhone: String(form.get("guestPhone")).trim(),
		checkInDate: form.get("checkInDate"),
		checkOutDate: form.get("checkOutDate")
	};
	const submitButton = bookingForm.querySelector('[type="submit"]');
	submitButton.disabled = true;
	bookingError.hidden = true;
	try {
		const order = await request("/payment/create-order", {
			method: "POST",
			body: JSON.stringify(requestBody)
		});
		pendingPaymentOrder = order;
		document.querySelector("#payment-room").textContent =
			document.querySelector("#dialog-room").textContent;
		document.querySelector("#payment-subtotal").textContent = formatCurrency(order.subtotal);
		document.querySelector("#payment-gst-rate").textContent = order.gstRatePercent;
		document.querySelector("#payment-gst").textContent = formatCurrency(order.gstAmount);
		document.querySelector("#payment-total").textContent = formatCurrency(order.totalAmount);
		document.querySelector("#payment-error").hidden = true;
		const payButton = document.querySelector("#pay-now");
		payButton.textContent = order.testMode ? "Pay Now (Test Mode)" : "Pay Now with Razorpay";
		payButton.disabled = false;
		bookingDialog.close();
		paymentDialog.showModal();
	} catch (error) {
		bookingError.textContent = error.message || "We couldn't complete your booking. Please try again.";
		bookingError.hidden = false;
	} finally {
		submitButton.disabled = false;
	}
});

lookupForm.addEventListener("submit", async (event) => {
	event.preventDefault();
	if (!lookupForm.reportValidity()) return;
	await findBookings(document.querySelector("#lookup-email").value.trim());
});

document.addEventListener("hotel-auth-changed", (event) => {
	const user = event.detail;
	if (user) {
		document.querySelector("#lookup-email").value = user.email;
		bookingForm.elements.guestEmail.value = user.email;
		bookingForm.elements.guestEmail.readOnly = true;
		if (pendingRoom) {
			const room = pendingRoom;
			pendingRoom = null;
			openBooking(room);
		}
	}
});

window.addEventListener("hotel-toast", (event) => showToast(event.detail));

setInitialDates();
loadRooms();
