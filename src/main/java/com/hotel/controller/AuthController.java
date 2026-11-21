package com.hotel.controller;

import com.hotel.model.User;
import com.hotel.model.UserRole;
import com.hotel.repository.UserRepository;
import com.hotel.security.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final AuthenticationManager authenticationManager;
	private final JwtUtil jwtUtil;

	public AuthController(
			UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			AuthenticationManager authenticationManager,
			JwtUtil jwtUtil) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.authenticationManager = authenticationManager;
		this.jwtUtil = jwtUtil;
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public AuthResponse register(@RequestBody RegisterRequest request) {
		if (request == null || request.fullName() == null || request.fullName().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Full name is required");
		}
		if (request.fullName().trim().length() > 120) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Full name must be 120 characters or fewer");
		}
		String email = normalizeEmail(request.email());
		validateEmail(email);
		validatePassword(request.password());
		if (userRepository.existsByEmailIgnoreCase(email)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
		}

		User user = new User();
		user.setFullName(request.fullName().trim());
		user.setEmail(email);
		user.setPassword(passwordEncoder.encode(request.password()));
		user.setRole(UserRole.ROLE_CUSTOMER);
		User savedUser = userRepository.save(user);
		return toResponse(savedUser);
	}

	@PostMapping("/login")
	public AuthResponse login(@RequestBody LoginRequest request) {
		if (request == null || request.email() == null || request.password() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email and password are required");
		}
		String email = normalizeEmail(request.email());
		try {
			Authentication authentication = authenticationManager.authenticate(
					new UsernamePasswordAuthenticationToken(email, request.password()));
			UserDetails details = (UserDetails) authentication.getPrincipal();
			User user = userRepository.findByEmailIgnoreCase(details.getUsername())
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
			return toResponse(user);
		} catch (BadCredentialsException exception) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
		}
	}

	private AuthResponse toResponse(User user) {
		return new AuthResponse(
				jwtUtil.generateToken(user),
				user.getEmail(),
				user.getFullName(),
				user.getRole().name());
	}

	private String normalizeEmail(String email) {
		return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
	}

	private void validateEmail(String email) {
		if (email.length() > 254 || !EMAIL_PATTERN.matcher(email).matches()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid email address is required");
		}
	}

	private void validatePassword(String password) {
		if (password == null || password.length() < 8
				|| password.getBytes(StandardCharsets.UTF_8).length > 72) {
			throw new ResponseStatusException(
					HttpStatus.BAD_REQUEST, "Password must be at least 8 characters and no more than 72 UTF-8 bytes");
		}
	}

	public record RegisterRequest(String fullName, String email, String password) {
	}

	public record LoginRequest(String email, String password) {
	}

	public record AuthResponse(String token, String email, String fullName, String role) {
	}
}
