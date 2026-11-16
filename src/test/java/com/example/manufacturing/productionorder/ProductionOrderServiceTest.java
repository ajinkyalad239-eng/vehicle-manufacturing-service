package com.example.manufacturing.productionorder;

import com.example.manufacturing.common.exception.BusinessRuleException;
import com.example.manufacturing.common.exception.ResourceNotFoundException;
import com.example.manufacturing.plant.PlantService;
import com.example.manufacturing.plant.PlantStatus;
import com.example.manufacturing.productionorder.dto.ProductionOrderRequest;
import com.example.manufacturing.productionorder.dto.ProductionOrderResponse;
import com.example.manufacturing.support.TestData;
import com.example.manufacturing.vehiclemodel.VehicleModelService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.example.manufacturing.support.TestData.START;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductionOrderServiceTest {

    private static final Pageable PAGE = PageRequest.of(0, 20);

    @Mock
    private ProductionOrderRepository orderRepository;

    @Mock
    private PlantService plantService;

    @Mock
    private VehicleModelService vehicleModelService;

    @InjectMocks
    private ProductionOrderService orderService;

    /** Plant 1 builds 100/day, so 1000 vehicles fit exactly into 10 days. */
    private static ProductionOrderRequest request(int quantity, LocalDate start, LocalDate end, OrderPriority priority) {
        return new ProductionOrderRequest(1L, 10L, quantity, priority, start, end, "Launch batch");
    }

    private static ProductionOrderRequest validRequest() {
        return request(1000, START, START.plusDays(9), null);
    }

    private void stubPlantAndModel() {
        when(plantService.getPlant(1L)).thenReturn(TestData.activePlant());
        when(vehicleModelService.getVehicleModel(10L)).thenReturn(TestData.activeModel());
    }

    private void stubFind(ProductionOrder order) {
        when(orderRepository.findWithDetailsById(order.getId())).thenReturn(Optional.of(order));
    }

    private void stubSaveAndFlush() {
        when(orderRepository.saveAndFlush(any(ProductionOrder.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Nested
    class Read {

        @Test
        void findAll_passesFiltersAndMapsResults() {
            ProductionOrder order = TestData.order(5L, ProductionOrderStatus.IN_PROGRESS, 200, 50);
            when(orderRepository.search(ProductionOrderStatus.IN_PROGRESS, 1L, 10L, PAGE))
                    .thenReturn(new PageImpl<>(List.of(order)));

            List<ProductionOrderResponse> content =
                    orderService.findAll(ProductionOrderStatus.IN_PROGRESS, 1L, 10L, PAGE).getContent();

            assertThat(content).singleElement().satisfies(o -> {
                assertThat(o.orderNumber()).isEqualTo("PO-TEST-5");
                assertThat(o.plantId()).isEqualTo(1L);
                assertThat(o.plantCode()).isEqualTo("JP-TST-1");
                assertThat(o.vehicleModelId()).isEqualTo(10L);
                assertThat(o.modelCode()).isEqualTo("TST-10");
                assertThat(o.modelName()).isEqualTo("Test Model 10");
                assertThat(o.progressPercent()).isEqualTo(25);
            });
        }

        @Test
        void findById_existing_returnsOrder() {
            stubFind(TestData.order(5L, ProductionOrderStatus.PLANNED, 100, 0));

            assertThat(orderService.findById(5L).status()).isEqualTo(ProductionOrderStatus.PLANNED);
        }

        @Test
        void findById_missing_throwsNotFound() {
            when(orderRepository.findWithDetailsById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.findById(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Production order with id 99 was not found");
        }
    }

    @Nested
    class Create {

        @Test
        void validRequest_createsPlannedOrderWithDefaults() {
            stubPlantAndModel();
            // First generated number collides, second one is free: exercises the retry loop.
            when(orderRepository.existsByOrderNumber(anyString())).thenReturn(true, false);
            when(orderRepository.save(any(ProductionOrder.class))).thenAnswer(inv -> inv.getArgument(0));

            ProductionOrderResponse response = orderService.create(validRequest());

            assertThat(response.orderNumber()).matches("^PO-\\d{8}-[0-9A-F]{6}$");
            assertThat(response.status()).isEqualTo(ProductionOrderStatus.PLANNED);
            assertThat(response.priority()).isEqualTo(OrderPriority.MEDIUM);
            assertThat(response.quantity()).isEqualTo(1000);
            assertThat(response.completedQuantity()).isZero();
            assertThat(response.progressPercent()).isZero();
            assertThat(response.plannedStartDate()).isEqualTo(START);
            assertThat(response.plannedEndDate()).isEqualTo(START.plusDays(9));
            assertThat(response.notes()).isEqualTo("Launch batch");
            verify(orderRepository, times(2)).existsByOrderNumber(anyString());
        }

        @Test
        void givenPriority_isKept() {
            stubPlantAndModel();
            when(orderRepository.existsByOrderNumber(anyString())).thenReturn(false);
            when(orderRepository.save(any(ProductionOrder.class))).thenAnswer(inv -> inv.getArgument(0));

            ProductionOrderResponse response =
                    orderService.create(request(100, START, START, OrderPriority.CRITICAL));

            assertThat(response.priority()).isEqualTo(OrderPriority.CRITICAL);
        }

        @Test
        void plantNotActive_isRejected() {
            when(plantService.getPlant(1L)).thenReturn(TestData.plant(1L, PlantStatus.MAINTENANCE, 100));
            when(vehicleModelService.getVehicleModel(10L)).thenReturn(TestData.activeModel());

            assertThatThrownBy(() -> orderService.create(validRequest()))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("MAINTENANCE");
            verify(orderRepository, never()).save(any());
        }

        @Test
        void modelNotActive_isRejected() {
            when(plantService.getPlant(1L)).thenReturn(TestData.activePlant());
            when(vehicleModelService.getVehicleModel(10L)).thenReturn(TestData.vehicleModel(10L, false));

            assertThatThrownBy(() -> orderService.create(validRequest()))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("not active");
        }

        @Test
        void endBeforeStart_isRejected() {
            stubPlantAndModel();

            assertThatThrownBy(() -> orderService.create(request(10, START, START.minusDays(1), null)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("plannedEndDate");
        }

        @Test
        void quantityAbovePlantCapacity_isRejected() {
            stubPlantAndModel();

            // 1001 vehicles at 100/day need 11 days, but only 10 are planned.
            assertThatThrownBy(() -> orderService.create(request(1001, START, START.plusDays(9), null)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("at least 11 days");
        }
    }

    @Nested
    class Update {

        @Test
        void plannedOrder_isReplanned() {
            ProductionOrder order = TestData.order(5L, ProductionOrderStatus.PLANNED, 100, 0);
            stubFind(order);
            stubPlantAndModel();
            stubSaveAndFlush();

            ProductionOrderResponse response =
                    orderService.update(5L, request(300, START, START.plusDays(4), OrderPriority.HIGH));

            assertThat(response.quantity()).isEqualTo(300);
            assertThat(response.priority()).isEqualTo(OrderPriority.HIGH);
            assertThat(response.plannedEndDate()).isEqualTo(START.plusDays(4));
        }

        @Test
        void orderNotPlanned_isRejected() {
            stubFind(TestData.order(5L, ProductionOrderStatus.IN_PROGRESS, 100, 10));

            assertThatThrownBy(() -> orderService.update(5L, validRequest()))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Only PLANNED orders can be edited");
            verify(orderRepository, never()).saveAndFlush(any());
        }
    }

    @Nested
    class ChangeStatus {

        @Test
        void start_setsActualStart() {
            stubFind(TestData.order(5L, ProductionOrderStatus.PLANNED, 100, 0));
            stubSaveAndFlush();

            ProductionOrderResponse response = orderService.changeStatus(5L, ProductionOrderStatus.IN_PROGRESS);

            assertThat(response.status()).isEqualTo(ProductionOrderStatus.IN_PROGRESS);
            assertThat(response.actualStartAt()).isNotNull();
            assertThat(response.actualEndAt()).isNull();
        }

        @Test
        void resumeFromHold_keepsOriginalActualStart() {
            Instant originalStart = Instant.parse("2026-12-01T00:00:00Z");
            ProductionOrder order = TestData.order(5L, ProductionOrderStatus.ON_HOLD, 100, 40);
            order.setActualStartAt(originalStart);
            stubFind(order);
            stubSaveAndFlush();

            ProductionOrderResponse response = orderService.changeStatus(5L, ProductionOrderStatus.IN_PROGRESS);

            assertThat(response.actualStartAt()).isEqualTo(originalStart);
        }

        @Test
        void hold_changesOnlyStatus() {
            stubFind(TestData.order(5L, ProductionOrderStatus.IN_PROGRESS, 100, 40));
            stubSaveAndFlush();

            ProductionOrderResponse response = orderService.changeStatus(5L, ProductionOrderStatus.ON_HOLD);

            assertThat(response.status()).isEqualTo(ProductionOrderStatus.ON_HOLD);
            assertThat(response.completedQuantity()).isEqualTo(40);
            assertThat(response.actualEndAt()).isNull();
        }

        @Test
        void complete_fillsQuantityAndSetsActualEnd() {
            stubFind(TestData.order(5L, ProductionOrderStatus.IN_PROGRESS, 100, 40));
            stubSaveAndFlush();

            ProductionOrderResponse response = orderService.changeStatus(5L, ProductionOrderStatus.COMPLETED);

            assertThat(response.completedQuantity()).isEqualTo(100);
            assertThat(response.progressPercent()).isEqualTo(100);
            assertThat(response.actualEndAt()).isNotNull();
        }

        @Test
        void cancel_setsActualEnd() {
            stubFind(TestData.order(5L, ProductionOrderStatus.PLANNED, 100, 0));
            stubSaveAndFlush();

            ProductionOrderResponse response = orderService.changeStatus(5L, ProductionOrderStatus.CANCELLED);

            assertThat(response.status()).isEqualTo(ProductionOrderStatus.CANCELLED);
            assertThat(response.actualEndAt()).isNotNull();
        }

        @Test
        void invalidTransition_isRejected() {
            stubFind(TestData.order(5L, ProductionOrderStatus.COMPLETED, 100, 100));

            assertThatThrownBy(() -> orderService.changeStatus(5L, ProductionOrderStatus.IN_PROGRESS))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("from COMPLETED to IN_PROGRESS");
            verify(orderRepository, never()).saveAndFlush(any());
        }
    }

    @Nested
    class UpdateProgress {

        @Test
        void partialProgress_staysInProgress() {
            stubFind(TestData.order(5L, ProductionOrderStatus.IN_PROGRESS, 100, 10));
            stubSaveAndFlush();

            ProductionOrderResponse response = orderService.updateProgress(5L, 60);

            assertThat(response.completedQuantity()).isEqualTo(60);
            assertThat(response.status()).isEqualTo(ProductionOrderStatus.IN_PROGRESS);
            assertThat(response.actualEndAt()).isNull();
        }

        @Test
        void reachingQuantity_completesOrder() {
            stubFind(TestData.order(5L, ProductionOrderStatus.IN_PROGRESS, 100, 10));
            stubSaveAndFlush();

            ProductionOrderResponse response = orderService.updateProgress(5L, 100);

            assertThat(response.status()).isEqualTo(ProductionOrderStatus.COMPLETED);
            assertThat(response.actualEndAt()).isNotNull();
        }

        @Test
        void orderNotInProgress_isRejected() {
            stubFind(TestData.order(5L, ProductionOrderStatus.PLANNED, 100, 0));

            assertThatThrownBy(() -> orderService.updateProgress(5L, 10))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("IN_PROGRESS");
        }

        @Test
        void moreThanOrdered_isRejected() {
            stubFind(TestData.order(5L, ProductionOrderStatus.IN_PROGRESS, 100, 10));

            assertThatThrownBy(() -> orderService.updateProgress(5L, 101))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("exceeds");
        }

        @Test
        void decreasingProgress_isRejected() {
            stubFind(TestData.order(5L, ProductionOrderStatus.IN_PROGRESS, 100, 50));

            assertThatThrownBy(() -> orderService.updateProgress(5L, 49))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("cannot go down");
        }
    }

    @Nested
    class Delete {

        @Test
        void plannedOrder_isDeleted() {
            ProductionOrder order = TestData.order(5L, ProductionOrderStatus.PLANNED, 100, 0);
            stubFind(order);

            orderService.delete(5L);

            verify(orderRepository).delete(order);
        }

        @Test
        void cancelledOrder_isDeleted() {
            ProductionOrder order = TestData.order(5L, ProductionOrderStatus.CANCELLED, 100, 0);
            stubFind(order);

            orderService.delete(5L);

            verify(orderRepository).delete(order);
        }

        @Test
        void startedOrder_isRejected() {
            stubFind(TestData.order(5L, ProductionOrderStatus.IN_PROGRESS, 100, 10));

            assertThatThrownBy(() -> orderService.delete(5L))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("cancel it first");
            verify(orderRepository, never()).delete(any());
        }
    }
}
