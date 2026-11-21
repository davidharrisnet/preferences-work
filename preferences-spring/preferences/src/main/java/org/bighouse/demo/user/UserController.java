package org.bighouse.demo.user;

import java.net.URI;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/users")
@Tag(name = "Users", description = "Look up and create users")
public class UserController {

	private final UserRepository userRepository;

	public UserController(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Operation(summary = "Get a user by id")
	@ApiResponse(responseCode = "200", description = "User found")
	@ApiResponse(responseCode = "404", description = "No user with that id", content = @Content)
	@GetMapping("/{id}")
	public ResponseEntity<User> getUser(@Parameter(description = "User id", example = "1") @PathVariable Long id) {
		return userRepository.findById(id)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@Operation(summary = "Create a user", description = "Saves a new user and returns it with its generated id.")
	@ApiResponse(responseCode = "201", description = "User created; the Location header holds its URL")
	@ApiResponse(responseCode = "400", description = "Invalid input (blank name or gender, or age outside 0-150)", content = @Content)
	@PostMapping
	public ResponseEntity<User> createUser(@Valid @RequestBody CreateUserRequest request) {
		User saved = userRepository.save(request.toUser());
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}")
				.buildAndExpand(saved.getId())
				.toUri();
		return ResponseEntity.created(location).body(saved);
	}

}
