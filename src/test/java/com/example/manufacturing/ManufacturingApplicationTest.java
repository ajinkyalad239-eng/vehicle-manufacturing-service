package com.example.manufacturing;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

import static org.mockito.Mockito.mockStatic;

class ManufacturingApplicationTest {

    @Test
    void main_startsSpringApplication() {
        String[] args = {};
        try (MockedStatic<SpringApplication> spring = mockStatic(SpringApplication.class)) {
            ManufacturingApplication.main(args);
            spring.verify(() -> SpringApplication.run(ManufacturingApplication.class, args));
        }
    }
}
