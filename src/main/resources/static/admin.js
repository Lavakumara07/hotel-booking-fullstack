const roomsTableBody = document.querySelector("#rooms-table-body");
const bookingsTableBody = document.querySelector("#bookings-table-body");
const adminToast = document.querySelector("#admin-toast");
let adminToastTimer;

function adminCurrency(amount) {
	return new Intl.NumberFormat("en-IN", {
		style: "currency",
		currency: "INR",
		maximumFractionDigits: 0
	}).format(Number(amount) || 0);
}

function adminDate(value) {
	if (!value) return "—";
	return new Intl.DateTimeFormat("en-IN", {
		day: "numeric",
		month: "short",
		year: "numeric"
	}).format(new Date(`${value}T00:00:00`));
}

function adminRequest(path, options = {}) {
	return window.HotelAuth.request(path, options);
}

function showAdminToast(message, isError = false) {
	window.clearTimeout(adminToastTimer);
	adminToast.textContent = message;
	adminToast.classList.toggle("toast-error", isError);
	adminToast.classList.add("visible");
	adminToastTimer = window.setTimeout(() => adminToast.classList.remove("visible"), 4500);
}

function appendCell(row, value, className = "") {
	const cell = document.createElement("td");
	if (className) cell.className = className;
	cell.textContent = value == null || value === "" ? "—" : String(value);
	row.append(cell);
	return cell;
}

function createRoomRow(room) {
	const row = document.createElement("tr");
	appendCell(row, room.roomNumber);
	appendCell(row, room.roomType);
	appendCell(row, adminCurrency(room.pricePerNight));

	const availabilityCell = document.createElement("td");
	const availability = document.createElement("span");
	availability.className = `admin-status${room.available ? "" : " admin-status-unavailable"}`;
	availability.textContent = room.available ? "Listed" : "Unavailable";
	availabilityCell.append(availability);
	row.append(availabilityCell);

	const actions = document.createElement("td");
	actions.className = "admin-actions-cell";
	const priceForm = document.createElement("form");
	priceForm.className = "price-edit-form";
	priceForm.dataset.roomId = room.id;
	const priceInput = document.createElement("input");
	priceInput.type = "number";
	priceInput.name = "pricePerNight";
	priceInput.min = "0.01";
	priceInput.step = "0.01";
	priceInput.value = room.pricePerNight;
	priceInput.setAttribute("aria-label", `New price for room ${room.roomNumber}`);
	priceInput.required = true;
	const saveButton = document.createElement("button");
	saveButton.className = "table-action";
	saveButton.type = "submit";
	saveButton.textContent = "Save price";
	priceForm.append(priceInput, saveButton);

	const deleteButton = document.createElement("button");
	deleteButton.className = "table-action table-action-danger";
	deleteButton.type = "button";
	deleteButton.textContent = "Delete";
	deleteButton.dataset.roomId = room.id;
	deleteButton.dataset.roomNumber = room.roomNumber;
	actions.append(priceForm, deleteButton);
	row.append(actions);
	return row;
}

function renderRooms(rooms) {
	roomsTableBody.replaceChildren();
	document.querySelector("#room-count").textContent = String(rooms.length);
	if (!rooms.length) {
		const row = document.createElement("tr");
		const cell = document.createElement("td");
		cell.className = "admin-table-message";
		cell.colSpan = 5;
		cell.textContent = "No rooms yet. Add your first room above.";
		row.append(cell);
		roomsTableBody.append(row);
		return;
	}
	for (const room of rooms) roomsTableBody.append(createRoomRow(room));
}

function renderBookings(bookings) {
	bookingsTableBody.replaceChildren();
	document.querySelector("#booking-count").textContent = String(bookings.length);
	document.querySelector("#active-booking-count").textContent = String(
		bookings.filter((booking) => String(booking.status || "").toLowerCase() !== "cancelled").length
	);
	if (!bookings.length) {
		const row = document.createElement("tr");
		const cell = document.createElement("td");
		cell.className = "admin-table-message";
		cell.colSpan = 5;
		cell.textContent = "No bookings have been recorded yet.";
		row.append(cell);
		bookingsTableBody.append(row);
		return;
	}

	for (const booking of bookings) {
		const row = document.createElement("tr");
		const guestCell = document.createElement("td");
		const guestName = document.createElement("strong");
		guestName.className = "table-primary";
		guestName.textContent = booking.guestName || "Guest";
		const guestEmail = document.createElement("span");
		guestEmail.className = "table-secondary";
		guestEmail.textContent = booking.guestEmail || "";
		const guestPhone = document.createElement("span");
		guestPhone.className = "table-secondary";
		guestPhone.textContent = booking.guestPhone || "";
		guestCell.append(guestName, guestEmail, guestPhone);
		row.append(guestCell);

		const roomName = booking.room?.roomType
			? `${booking.room.roomType}${booking.room.roomNumber ? ` · ${booking.room.roomNumber}` : ""}`
			: "—";
		appendCell(row, roomName);
		appendCell(row, `${adminDate(booking.checkInDate)} – ${adminDate(booking.checkOutDate)}`);
		appendCell(row, adminCurrency(booking.totalPrice));

		const statusCell = document.createElement("td");
		const status = document.createElement("span");
		const isCancelled = String(booking.status || "").toLowerCase() === "cancelled";
		status.className = `admin-status${isCancelled ? " admin-status-cancelled" : ""}`;
		status.textContent = booking.status || "Pending";
		statusCell.append(status);
		row.append(statusCell);
		bookingsTableBody.append(row);
	}
}

async function refreshDashboard() {
	const user = window.HotelAuth.getUser();
	if (!user || user.role !== "ROLE_ADMIN") {
		const roomRow = document.createElement("tr");
		const roomCell = document.createElement("td");
		roomCell.className = "admin-table-message";
		roomCell.colSpan = 5;
		roomCell.textContent = "Sign in with an administrator account to manage rooms.";
		roomRow.append(roomCell);
		roomsTableBody.replaceChildren(roomRow);
		const bookingRow = document.createElement("tr");
		const bookingCell = document.createElement("td");
		bookingCell.className = "admin-table-message";
		bookingCell.colSpan = 5;
		bookingCell.textContent = "Administrator access is required to view customer bookings.";
		bookingRow.append(bookingCell);
		bookingsTableBody.replaceChildren(bookingRow);
		if (user) showAdminToast("This page requires an administrator account.", true);
		return;
	}
	document.querySelector("#refresh-dashboard").disabled = true;
	try {
		const [rooms, bookings] = await Promise.all([
			adminRequest("/rooms"),
			adminRequest("/bookings")
		]);
		renderRooms(rooms);
		renderBookings(bookings);
		document.querySelector("#last-updated").textContent =
			`Updated ${new Intl.DateTimeFormat("en-IN", { hour: "numeric", minute: "2-digit" }).format(new Date())}`;
	} catch (error) {
		showAdminToast(error.message || "Unable to load dashboard data.", true);
	} finally {
		document.querySelector("#refresh-dashboard").disabled = false;
	}
}

document.querySelector("#add-room-form").addEventListener("submit", async (event) => {
	event.preventDefault();
	const form = event.currentTarget;
	if (!form.reportValidity()) return;
	const submitButton = form.querySelector('[type="submit"]');
	const values = new FormData(form);
	const body = {
		roomNumber: String(values.get("roomNumber")).trim(),
		roomType: String(values.get("roomType")).trim(),
		pricePerNight: Number(values.get("pricePerNight")),
		description: String(values.get("description") || "").trim(),
		imageUrl: String(values.get("imageUrl") || "").trim()
	};
	submitButton.disabled = true;
	try {
		await adminRequest("/rooms", { method: "POST", body: JSON.stringify(body) });
		form.reset();
		form.closest("details").open = false;
		showAdminToast("Room added to the collection.");
		await refreshDashboard();
	} catch (error) {
		showAdminToast(error.message || "Unable to add this room.", true);
	} finally {
		submitButton.disabled = false;
	}
});

roomsTableBody.addEventListener("submit", async (event) => {
	if (!event.target.matches(".price-edit-form")) return;
	event.preventDefault();
	const form = event.target;
	if (!form.reportValidity()) return;
	const input = form.elements.pricePerNight;
	const button = form.querySelector('[type="submit"]');
	button.disabled = true;
	try {
		await adminRequest(`/rooms/${encodeURIComponent(form.dataset.roomId)}/price`, {
			method: "PATCH",
			body: JSON.stringify({ pricePerNight: Number(input.value) })
		});
		showAdminToast("Room price updated.");
		await refreshDashboard();
	} catch (error) {
		showAdminToast(error.message || "Unable to update the room price.", true);
	} finally {
		button.disabled = false;
	}
});

roomsTableBody.addEventListener("click", async (event) => {
	const button = event.target.closest(".table-action-danger");
	if (!button) return;
	const roomNumber = button.dataset.roomNumber;
	if (!window.confirm(`Delete room ${roomNumber}? Rooms with booking history cannot be deleted.`)) return;
	button.disabled = true;
	try {
		await adminRequest(`/rooms/${encodeURIComponent(button.dataset.roomId)}`, { method: "DELETE" });
		showAdminToast(`Room ${roomNumber} deleted.`);
		await refreshDashboard();
	} catch (error) {
		showAdminToast(error.message || "Unable to delete this room.", true);
		button.disabled = false;
	}
});

document.querySelector("#refresh-dashboard").addEventListener("click", refreshDashboard);
document.addEventListener("hotel-auth-changed", refreshDashboard);
window.addEventListener("hotel-toast", (event) => showAdminToast(event.detail));
if (window.HotelAuth.getUser()) {
	refreshDashboard();
} else {
	window.HotelAuth.open("login");
	refreshDashboard();
}
