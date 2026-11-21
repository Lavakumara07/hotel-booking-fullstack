package com.hotel.config;

import com.hotel.model.User;
import com.hotel.model.UserRole;
import com.hotel.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminUserSeeder implements CommandLineRunner {

	private static final String ADMIN_EMAIL = "admin@hotel.com";

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final String adminPassword;

	public AdminUserSeeder(
			UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			@Value("${app.security.admin-password:admin123}") String adminPassword) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.adminPassword = adminPassword;
	}

	@Override
	public void run(String... args) {
		if (userRepository.existsByEmailIgnoreCase(ADMIN_EMAIL)) {
			return;
		}

		User admin = new User();
		admin.setFullName("Hotel Administrator");
		admin.setEmail(ADMIN_EMAIL);
		admin.setPassword(passwordEncoder.encode(adminPassword));
		admin.setRole(UserRole.ROLE_ADMIN);
		userRepository.save(admin);
	}
}
