package com.udhaya.orderservice.service;

import com.udhaya.orderservice.dto.InventoryResponse;
import com.udhaya.orderservice.dto.OrderLineItemsDto;
import com.udhaya.orderservice.dto.OrderRequest;
import com.udhaya.orderservice.model.Order;
import com.udhaya.orderservice.model.OrderLineItems;
import com.udhaya.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final WebClient.Builder webClientBuilder;
    private final DiscoveryClient discoveryClient;

    public void placeOrder(OrderRequest orderRequest) {
        if (orderRequest == null || orderRequest.getOrderLineItemsDtoList() == null) {
            throw new IllegalArgumentException("Order request or order line items list is null");
        }
        
//        if (orderRequest == null) {
//            throw new IllegalArgumentException("Order request is null");
//        }
//
//        System.out.println("---------------------"+orderRequest);
//        if (orderRequest.getOrderLineItemsDtoList() == null) {
//            throw new IllegalArgumentException("Order line items list is null");
//        }

        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());

        List<OrderLineItems> orderLineItems = orderRequest.getOrderLineItemsDtoList()
                .stream()
                .map(this::mapToDto)
                .toList();

        order.setOrderLineItemsList(orderLineItems);

        if (order.getOrderLineItemsList() == null || order.getOrderLineItemsList().isEmpty()) {
            throw new IllegalArgumentException("Order line items list is empty");
        }

        List<String> skuCodes = order.getOrderLineItemsList().stream()
                .map(OrderLineItems::getSkuCode)
                .toList();

        System.out.println("---------------------------------------------"+discoveryClient.getInstances("inventory-service").stream().map(ServiceInstance::getUri).findFirst()
        );
        // Call Inventory Service, and place order if product is in stock
        InventoryResponse[] inventoryResponseArray = webClientBuilder.build().get()
                .uri("http://inventory-service/api/inventory",
                        uriBuilder -> uriBuilder.queryParam("skuCode", skuCodes).build())
                .retrieve()
                .bodyToMono(InventoryResponse[].class)
                .block();

        if (inventoryResponseArray == null || inventoryResponseArray.length != skuCodes.size()) {
            throw new IllegalStateException("Failed to retrieve inventory information for all sku codes");
        }

        boolean allProductsInStock = Arrays.stream(inventoryResponseArray)
                .allMatch(InventoryResponse::isInStock);

        if (allProductsInStock) {
            orderRepository.save(order);
        } else {
            throw new IllegalArgumentException("Product is not in stock, please try again later");
        }
    }

    private OrderLineItems mapToDto(OrderLineItemsDto orderLineItemsDto) {
        OrderLineItems orderLineItems = new OrderLineItems();
        orderLineItems.setPrice(orderLineItemsDto.getPrice());
        orderLineItems.setQuantity(orderLineItemsDto.getQuantity());
        orderLineItems.setSkuCode(orderLineItemsDto.getSkuCode());
        return orderLineItems;
    }
}
