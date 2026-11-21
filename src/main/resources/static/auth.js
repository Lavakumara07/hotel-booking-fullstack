(() => {
	const TOKEN_KEY = "hotelJwt";
	const USER_KEY = "hotelUser";
	const authDialog = document.querySelector("#auth-dialog");
	let currentUser = readUser();

	function readUser() {
		if (!localStorage.getItem(TOKEN_KEY)) {
			localStorage.removeItem(USER_KEY);
			return null;
		}
		try {
			return JSON.parse(localStorage.getItem(USER_KEY) || "null");
		} catch {
			localStorage.removeItem(USER_KEY);
			return null;
		}
	}

	function setSession(session) {
		localStorage.setItem(TOKEN_KEY, session.token);
		currentUser = {
			email: session.email,
			fullName: session.fullName,
			role: session.role
		};
		localStorage.setItem(USER_KEY, JSON.stringify(currentUser));
		updateAction();
		document.dispatchEvent(new CustomEvent("hotel-auth-changed", { detail: currentUser }));
	}

	function clearSession() {
		const hadSession = Boolean(localStorage.getItem(TOKEN_KEY) || currentUser);
		localStorage.removeItem(TOKEN_KEY);
		localStorage.removeItem(USER_KEY);
		currentUser = null;
		updateAction();
		if (hadSession) {
			document.dispatchEvent(new CustomEvent("hotel-auth-changed", { detail: null }));
		}
	}

	function updateAction() {
		const action = document.querySelector("#auth-action");
		if (!action) return;
		action.textContent = currentUser ? "Sign out" : "Log in";
		action.setAttribute("aria-label", currentUser ? `Sign out ${currentUser.fullName}` : "Log in or create an account");
		action.toggleAttribute("data-open-auth", !currentUser);
	}

	function setMode(mode) {
		const register = mode === "register";
		document.querySelector("#login-form").hidden = register;
		document.querySelector("#register-form").hidden = !register;
		document.querySelector("#auth-error").hidden = true;
		for (const tab of document.querySelectorAll("[data-auth-mode]")) {
			const selected = tab.dataset.authMode === mode;
			tab.classList.toggle("active", selected);
			tab.setAttribute("aria-selected", String(selected));
		}
	}

	function open(mode = "login") {
		setMode(mode);
		if (!authDialog.open) authDialog.showModal();
	}

	async function request(path, options = {}, includeToken = true) {
		const headers = {
			...(options.body ? { "Content-Type": "application/json" } : {}),
			...options.headers
		};
		const token = includeToken ? localStorage.getItem(TOKEN_KEY) : null;
		if (token) headers.Authorization = `Bearer ${token}`;

		const response = await fetch(`/api${path}`, { ...options, headers });
		const contentType = response.headers.get("content-type") || "";
		const payload = contentType.includes("json")
			? await response.json()
			: await response.text();

		if (!response.ok) {
			if (response.status === 401 && !path.startsWith("/auth/")) clearSession();
			const message = typeof payload === "string"
				? payload
				: payload.detail || payload.message || payload.error || payload.title;
			throw new Error(message || `Request failed (${response.status})`);
		}
		return payload;
	}

	document.querySelector("#auth-action").addEventListener("click", () => {
		if (currentUser) {
			clearSession();
			window.dispatchEvent(new CustomEvent("hotel-toast", { detail: "You have signed out." }));
		} else {
			open("login");
		}
	});

	document.querySelector("#auth-close").addEventListener("click", () => authDialog.close());
	authDialog.addEventListener("click", (event) => {
		if (event.target === authDialog) authDialog.close();
	});
	for (const tab of document.querySelectorAll("[data-auth-mode]")) {
		tab.addEventListener("click", () => setMode(tab.dataset.authMode));
	}

	for (const [mode, formId] of [["login", "login-form"], ["register", "register-form"]]) {
		const form = document.querySelector(`#${formId}`);
		form.addEventListener("submit", async (event) => {
			event.preventDefault();
			if (!form.reportValidity()) return;
			const values = new FormData(form);
			const body = Object.fromEntries(values.entries());
			if (body.email) body.email = String(body.email).trim();
			if (body.fullName) body.fullName = String(body.fullName).trim();
			const button = form.querySelector('[type="submit"]');
			const error = document.querySelector("#auth-error");
			button.disabled = true;
			error.hidden = true;
			try {
				const session = await request(`/auth/${mode}`, {
					method: "POST",
					body: JSON.stringify(body)
				}, false);
				authDialog.close();
				setSession(session);
				form.reset();
				window.dispatchEvent(new CustomEvent("hotel-toast", {
					detail: `Welcome${session.fullName ? `, ${session.fullName}` : ""}.`
				}));
			} catch (requestError) {
				error.textContent = requestError.message || "Unable to authenticate. Please try again.";
				error.hidden = false;
			} finally {
				button.disabled = false;
			}
		});
	}

	updateAction();
	window.HotelAuth = {
		getUser: () => currentUser,
		getToken: () => localStorage.getItem(TOKEN_KEY),
		open,
		request
	};
})();
