package edu.just.mashoora.controllers;


import edu.just.mashoora.constants.ERole;
import edu.just.mashoora.jwt.JwtUtils;
import edu.just.mashoora.models.Role;
import edu.just.mashoora.models.User;
import edu.just.mashoora.payload.request.LoginRequest;
import edu.just.mashoora.payload.request.SignupRequest;
import edu.just.mashoora.payload.response.JwtResponse;
import edu.just.mashoora.payload.response.MessageResponse;
import edu.just.mashoora.repository.RoleRepository;
import edu.just.mashoora.repository.UserRepository;
import edu.just.mashoora.services.EmailVerificationServiceImpl;
import edu.just.mashoora.services.UserDetailsImpl;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoleRepository roleRepository;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    EmailVerificationServiceImpl emailService;

    @Autowired
    JwtUtils jwtUtils;

    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {

        String username = loginRequest.getUsername();
        String password = loginRequest.getPassword();

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        List<String> roles = userDetails.getAuthorities().stream()
                .map(item -> item.getAuthority())
                .collect(Collectors.toList());

        return ResponseEntity.ok(
                JwtResponse.builder()
                        .token(jwt)
                        .type("Bearer")
                        .id(userDetails.getId())
                        .username(userDetails.getUsername())
                        .email(userDetails.getEmail())
                        .roles(roles)
                        .build()
        );
    }

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@RequestBody SignupRequest signUpRequest) {

        String username = signUpRequest.getUsername();
        String email = signUpRequest.getEmail();

        // Check if username exists
        if (userRepository.existsByUsername(username)) {
            return ResponseEntity
                    .badRequest()
                    .body(new MessageResponse("Error: Username is already taken!"));
        }

        // Check if email exists and is enabled
        User existingUser = userRepository.findByEmailAndEnabledTrue(email);
        if (existingUser != null) {
            return ResponseEntity
                    .badRequest()
                    .body(new MessageResponse("Error: Email is already in use and verified!"));
        }

        // Check if email exists but is not enabled
        existingUser = userRepository.findByEmailAndEnabledFalse(email);
        if (existingUser != null) {
            // Delete any unverified accounts with the same email
            userRepository.deleteByEmailAndEnabledFalse(email);
        }

        // Create new user's account
        User user = User.builder()
                .username(signUpRequest.getUsername())
                .firstName(signUpRequest.getFirstName())
                .lastName(signUpRequest.getLastName())
                .email(email)
                .password(encoder.encode(signUpRequest.getPassword()))
                .enabled(false) // Set user as disabled until email verification
                .build();

        // Assign roles
        Set<String> strRoles = signUpRequest.getRole();
        Set<Role> roles = new HashSet<>();

        if (strRoles == null) {
            Role customerRole = roleRepository.findByName(ERole.ROLE_CUSTOMER)
                    .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
            roles.add(customerRole);
        } else {
            strRoles.forEach(role -> {
                switch (role) {
                    case "admin":
                        Role adminRole = roleRepository.findByName(ERole.ROLE_ADMIN)
                                .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
                        roles.add(adminRole);
                        break;
                    case "lawyer":
                        Role lawyerRole = roleRepository.findByName(ERole.ROLE_LAWYER)
                                .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
                        roles.add(lawyerRole);
                        break;
                    default:
                        Role customerRole = roleRepository.findByName(ERole.ROLE_CUSTOMER)
                                .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
                        roles.add(customerRole);
                }
            });
        }

        user.setRoles(roles);

        // Save user
        User savedUser = userRepository.save(user);

        // Generate verification token
        String verificationToken = UUID.randomUUID().toString();
        savedUser.setVerificationToken(verificationToken);
        userRepository.save(savedUser);

        // Send verification email
        emailService.sendVerificationEmail(savedUser.getEmail(), savedUser.getId(), verificationToken);

        return ResponseEntity.ok(new MessageResponse("User registered successfully! Please check your email for verification."));
    }

    @GetMapping("/verify")
    public ResponseEntity<String> verifyUser(@RequestParam("userId") Long userId, @RequestParam("token") String token) {
        // Find the user by userId
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Check if the provided token matches the user's verification token
        if (token.equals(user.getVerificationToken())) {
            // Update user's status to enabled
            user.setEnabled(true);
            user.setVerificationToken(null);
            userRepository.save(user);
            return ResponseEntity.ok("User verified successfully");
        } else {
            return ResponseEntity.badRequest().body("Invalid verification token");
        }
    }

}
