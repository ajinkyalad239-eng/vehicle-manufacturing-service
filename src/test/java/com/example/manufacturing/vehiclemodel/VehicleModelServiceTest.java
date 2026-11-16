package com.example.manufacturing.vehiclemodel;

import com.example.manufacturing.common.exception.BusinessRuleException;
import com.example.manufacturing.common.exception.DuplicateResourceException;
import com.example.manufacturing.common.exception.ResourceNotFoundException;
import com.example.manufacturing.productionorder.ProductionOrderRepository;
import com.example.manufacturing.support.TestData;
import com.example.manufacturing.vehiclemodel.dto.VehicleModelRequest;
import com.example.manufacturing.vehiclemodel.dto.VehicleModelResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleModelServiceTest {

    private static final Pageable PAGE = PageRequest.of(0, 20);

    @Mock
    private VehicleModelRepository vehicleModelRepository;

    @Mock
    private ProductionOrderRepository productionOrderRepository;

    @InjectMocks
    private VehicleModelService vehicleModelService;

    private static VehicleModelRequest request(String code, Boolean active) {
        return new VehicleModelRequest(code, "Aurora EV", VehicleCategory.CROSSOVER, Powertrain.ELECTRIC,
                2025, new BigDecimal("4250000.00"), active);
    }

    @Test
    void findAll_passesFiltersToRepository() {
        when(vehicleModelRepository.search(Powertrain.ELECTRIC, true, PAGE))
                .thenReturn(new PageImpl<>(List.of(TestData.activeModel())));

        List<VehicleModelResponse> content = vehicleModelService.findAll(Powertrain.ELECTRIC, true, PAGE).getContent();

        assertThat(content).singleElement().satisfies(m -> {
            assertThat(m.modelCode()).isEqualTo("TST-10");
            assertThat(m.category()).isEqualTo(VehicleCategory.CROSSOVER);
            assertThat(m.powertrain()).isEqualTo(Powertrain.ELECTRIC);
            assertThat(m.launchYear()).isEqualTo(2025);
            assertThat(m.basePrice()).isEqualByComparingTo("4250000.00");
        });
    }

    @Test
    void findById_existing_returnsModel() {
        when(vehicleModelRepository.findById(10L)).thenReturn(Optional.of(TestData.activeModel()));

        assertThat(vehicleModelService.findById(10L).name()).isEqualTo("Test Model 10");
    }

    @Test
    void findById_missing_throwsNotFound() {
        when(vehicleModelRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vehicleModelService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Vehicle model with id 99 was not found");
    }

    @Test
    void create_withoutActiveFlag_defaultsToActive() {
        when(vehicleModelRepository.existsByModelCode("AUR-EV")).thenReturn(false);
        when(vehicleModelRepository.save(any(VehicleModel.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(vehicleModelService.create(request("AUR-EV", null)).active()).isTrue();
    }

    @Test
    void create_withActiveTrue_isActive() {
        when(vehicleModelRepository.existsByModelCode("AUR-EV")).thenReturn(false);
        when(vehicleModelRepository.save(any(VehicleModel.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(vehicleModelService.create(request("AUR-EV", true)).active()).isTrue();
    }

    @Test
    void create_withActiveFalse_isInactive() {
        when(vehicleModelRepository.existsByModelCode("AUR-EV")).thenReturn(false);
        when(vehicleModelRepository.save(any(VehicleModel.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(vehicleModelService.create(request("AUR-EV", false)).active()).isFalse();
    }

    @Test
    void create_duplicateCode_throwsConflict() {
        when(vehicleModelRepository.existsByModelCode("AUR-EV")).thenReturn(true);

        assertThatThrownBy(() -> vehicleModelService.create(request("AUR-EV", null)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("AUR-EV");
        verify(vehicleModelRepository, never()).save(any());
    }

    @Test
    void update_withoutActiveFlag_keepsCurrentFlag() {
        VehicleModel existing = TestData.vehicleModel(10L, false);
        when(vehicleModelRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(vehicleModelRepository.existsByModelCodeAndIdNot("AUR-EV", 10L)).thenReturn(false);
        when(vehicleModelRepository.saveAndFlush(existing)).thenReturn(existing);

        VehicleModelResponse response = vehicleModelService.update(10L, request("AUR-EV", null));

        assertThat(response.modelCode()).isEqualTo("AUR-EV");
        assertThat(response.active()).isFalse();
    }

    @Test
    void update_withActiveFlag_changesFlag() {
        VehicleModel existing = TestData.vehicleModel(10L, false);
        when(vehicleModelRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(vehicleModelRepository.existsByModelCodeAndIdNot("AUR-EV", 10L)).thenReturn(false);
        when(vehicleModelRepository.saveAndFlush(existing)).thenReturn(existing);

        assertThat(vehicleModelService.update(10L, request("AUR-EV", true)).active()).isTrue();
    }

    @Test
    void update_codeUsedByAnotherModel_throwsConflict() {
        when(vehicleModelRepository.findById(10L)).thenReturn(Optional.of(TestData.activeModel()));
        when(vehicleModelRepository.existsByModelCodeAndIdNot("TAKEN", 10L)).thenReturn(true);

        assertThatThrownBy(() -> vehicleModelService.update(10L, request("TAKEN", null)))
                .isInstanceOf(DuplicateResourceException.class);
        verify(vehicleModelRepository, never()).saveAndFlush(any());
    }

    @Test
    void delete_withoutOrders_deletesModel() {
        VehicleModel model = TestData.activeModel();
        when(vehicleModelRepository.findById(10L)).thenReturn(Optional.of(model));
        when(productionOrderRepository.existsByVehicleModelId(10L)).thenReturn(false);

        vehicleModelService.delete(10L);

        verify(vehicleModelRepository).delete(model);
    }

    @Test
    void delete_withOrders_throwsBusinessRule() {
        when(vehicleModelRepository.findById(10L)).thenReturn(Optional.of(TestData.activeModel()));
        when(productionOrderRepository.existsByVehicleModelId(10L)).thenReturn(true);

        assertThatThrownBy(() -> vehicleModelService.delete(10L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("active=false");
        verify(vehicleModelRepository, never()).delete(any());
    }
}
