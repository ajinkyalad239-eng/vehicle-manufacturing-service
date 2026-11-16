package com.example.manufacturing.plant;

import com.example.manufacturing.common.exception.BusinessRuleException;
import com.example.manufacturing.common.exception.DuplicateResourceException;
import com.example.manufacturing.common.exception.ResourceNotFoundException;
import com.example.manufacturing.plant.dto.PlantRequest;
import com.example.manufacturing.plant.dto.PlantResponse;
import com.example.manufacturing.productionorder.ProductionOrderRepository;
import com.example.manufacturing.support.TestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlantServiceTest {

    private static final Pageable PAGE = PageRequest.of(0, 20);

    @Mock
    private PlantRepository plantRepository;

    @Mock
    private ProductionOrderRepository productionOrderRepository;

    @InjectMocks
    private PlantService plantService;

    private static PlantRequest request(String code, PlantStatus status) {
        return new PlantRequest(code, "Kanto Assembly Plant", "Yokohama", "Japan", 1200, status);
    }

    @Test
    void findAll_withoutStatus_returnsAllPlants() {
        when(plantRepository.findAll(PAGE)).thenReturn(new PageImpl<>(List.of(TestData.activePlant())));

        Page<PlantResponse> result = plantService.findAll(null, PAGE);

        assertThat(result.getContent()).extracting(PlantResponse::code).containsExactly("JP-TST-1");
    }

    @Test
    void findAll_withStatus_filtersByStatus() {
        Plant plant = TestData.plant(2L, PlantStatus.MAINTENANCE, 50);
        when(plantRepository.findByStatus(PlantStatus.MAINTENANCE, PAGE)).thenReturn(new PageImpl<>(List.of(plant)));

        Page<PlantResponse> result = plantService.findAll(PlantStatus.MAINTENANCE, PAGE);

        assertThat(result.getContent()).extracting(PlantResponse::status).containsExactly(PlantStatus.MAINTENANCE);
    }

    @Test
    void findById_existing_returnsPlant() {
        when(plantRepository.findById(1L)).thenReturn(Optional.of(TestData.activePlant()));

        PlantResponse response = plantService.findById(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Test Plant 1");
        assertThat(response.city()).isEqualTo("Yokohama");
        assertThat(response.country()).isEqualTo("Japan");
        assertThat(response.dailyCapacity()).isEqualTo(100);
    }

    @Test
    void findById_missing_throwsNotFound() {
        when(plantRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> plantService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Plant with id 99 was not found");
    }

    @Test
    void create_withoutStatus_defaultsToActive() {
        when(plantRepository.existsByCode("JP-KNT-01")).thenReturn(false);
        when(plantRepository.save(any(Plant.class))).thenAnswer(inv -> inv.getArgument(0));

        PlantResponse response = plantService.create(request("JP-KNT-01", null));

        assertThat(response.code()).isEqualTo("JP-KNT-01");
        assertThat(response.status()).isEqualTo(PlantStatus.ACTIVE);
    }

    @Test
    void create_withStatus_keepsGivenStatus() {
        when(plantRepository.existsByCode("JP-KNT-01")).thenReturn(false);
        when(plantRepository.save(any(Plant.class))).thenAnswer(inv -> inv.getArgument(0));

        PlantResponse response = plantService.create(request("JP-KNT-01", PlantStatus.MAINTENANCE));

        assertThat(response.status()).isEqualTo(PlantStatus.MAINTENANCE);
    }

    @Test
    void create_duplicateCode_throwsConflict() {
        when(plantRepository.existsByCode("JP-KNT-01")).thenReturn(true);

        assertThatThrownBy(() -> plantService.create(request("JP-KNT-01", null)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("JP-KNT-01");
        verify(plantRepository, never()).save(any());
    }

    @Test
    void update_withoutStatus_keepsCurrentStatus() {
        Plant existing = TestData.plant(1L, PlantStatus.MAINTENANCE, 100);
        when(plantRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(plantRepository.existsByCodeAndIdNot("JP-NEW-01", 1L)).thenReturn(false);
        when(plantRepository.saveAndFlush(existing)).thenReturn(existing);

        PlantResponse response = plantService.update(1L, request("JP-NEW-01", null));

        assertThat(response.code()).isEqualTo("JP-NEW-01");
        assertThat(response.dailyCapacity()).isEqualTo(1200);
        assertThat(response.status()).isEqualTo(PlantStatus.MAINTENANCE);
    }

    @Test
    void update_withStatus_changesStatus() {
        Plant existing = TestData.activePlant();
        when(plantRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(plantRepository.existsByCodeAndIdNot("JP-TST-1", 1L)).thenReturn(false);
        when(plantRepository.saveAndFlush(existing)).thenReturn(existing);

        PlantResponse response = plantService.update(1L, request("JP-TST-1", PlantStatus.INACTIVE));

        assertThat(response.status()).isEqualTo(PlantStatus.INACTIVE);
    }

    @Test
    void update_codeUsedByAnotherPlant_throwsConflict() {
        when(plantRepository.findById(1L)).thenReturn(Optional.of(TestData.activePlant()));
        when(plantRepository.existsByCodeAndIdNot("JP-TAKEN", 1L)).thenReturn(true);

        assertThatThrownBy(() -> plantService.update(1L, request("JP-TAKEN", null)))
                .isInstanceOf(DuplicateResourceException.class);
        verify(plantRepository, never()).saveAndFlush(any());
    }

    @Test
    void delete_withoutOrders_deletesPlant() {
        Plant plant = TestData.activePlant();
        when(plantRepository.findById(1L)).thenReturn(Optional.of(plant));
        when(productionOrderRepository.existsByPlantId(1L)).thenReturn(false);

        plantService.delete(1L);

        verify(plantRepository).delete(plant);
    }

    @Test
    void delete_withOrders_throwsBusinessRule() {
        when(plantRepository.findById(1L)).thenReturn(Optional.of(TestData.activePlant()));
        when(productionOrderRepository.existsByPlantId(1L)).thenReturn(true);

        assertThatThrownBy(() -> plantService.delete(1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("INACTIVE");
        verify(plantRepository, never()).delete(any());
    }
}
