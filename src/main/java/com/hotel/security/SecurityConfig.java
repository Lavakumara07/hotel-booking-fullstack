package com.hotel.security;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			JwtUtil jwtUtil,
			CustomUserDetailsService userDetailsService) throws Exception {
		JwtFilter jwtFilter = new JwtFilter(jwtUtil, userDetailsService);
		return http
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(authorize -> authorize
						.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
						.requestMatchers(
								"/", "/index.html", "/style.css", "/script.js", "/auth.js",
								"/admin.html", "/admin.js", "/favicon.ico")
						.permitAll()
						.requestMatchers("/api/auth/**").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/rooms", "/api/rooms/available").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/rooms").hasRole("ADMIN")
						.requestMatchers(HttpMethod.PATCH, "/api/rooms/**").hasRole("ADMIN")
						.requestMatchers(HttpMethod.DELETE, "/api/rooms/**").hasRole("ADMIN")
						.requestMatchers("/api/payment/**").hasAnyRole("ADMIN", "CUSTOMER")
						.requestMatchers(HttpMethod.GET, "/api/bookings/mine").hasAnyRole("ADMIN", "CUSTOMER")
						.requestMatchers(HttpMethod.GET, "/api/bookings").hasRole("ADMIN")
						.requestMatchers(HttpMethod.PATCH, "/api/bookings/*/cancel")
						.hasAnyRole("ADMIN", "CUSTOMER")
						.anyRequest().denyAll())
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint((request, response, exception) ->
								writeSecurityError(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required"))
						.accessDeniedHandler((request, response, exception) ->
								writeSecurityError(response, HttpServletResponse.SC_FORBIDDEN, "Access denied")))
				.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
				.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
		return configuration.getAuthenticationManager();
	}

	private void writeSecurityError(HttpServletResponse response, int status, String message) throws IOException {
		response.setStatus(status);
		response.setContentType("application/json");
		response.getWriter().write("{\"error\":\"" + message + "\"}");
	}
}
