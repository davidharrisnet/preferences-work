package org.bighouse.demo.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Data for a new user")
public record CreateUserRequest(
		@Schema(description = "Full name", example = "Alice Smith")
		@NotBlank String name,

		@Schema(description = "Gender", example = "female")
		@NotBlank String gender,

		@Schema(description = "Age in years", example = "30", minimum = "0", maximum = "150")
		@NotNull @Min(0) @Max(150) Integer age) {

	User toUser() {
		return new User(name, gender, age);
	}

}
