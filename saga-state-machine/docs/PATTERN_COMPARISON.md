# Pattern Comparison: Orchestration vs Choreography

## Two Approaches to Sagas

### Orchestration (Used in this project)

A central **Saga Orchestrator** (SagaEngine) coordinates all steps.

```
          ┌─────────────────┐
          │  SagaEngine     │
          │  (Orchestrator) │
          └────────┬────────┘
                   │
     ┌─────────────┼─────────────┐
     │             │             │
     ▼             ▼             ▼
┌─────────┐  ┌─────────┐  ┌─────────┐
│Inventory│  │ Payment │  │Shipping │
│ Service │  │ Service │  │ Service │
└─────────┘  └─────────┘  └─────────┘
```

### Choreography (Alternative)

Services communicate via events, no central coordinator.

```
┌─────────┐     ┌─────────┐     ┌─────────┐
│Inventory│────▶│ Payment │────▶│Shipping │
│ Service │     │ Service │     │ Service │
└─────────┘     └─────────┘     └─────────┘
     │               │               │
     │      Events   │               │
     ▼               ▼               ▼
┌─────────────────────────────────────────┐
│           Event Bus (Kafka)             │
└─────────────────────────────────────────┘
```

## Comparison Table

| Aspect | Orchestration | Choreography |
|--------|---------------|--------------|
| **Coordination** | Central orchestrator | Distributed, event-driven |
| **Coupling** | Services coupled to orchestrator | Services coupled via events |
| **Complexity** | In orchestrator | Distributed across services |
| **Visibility** | Easy to see full flow | Hard to trace flow |
| **Single Point of Failure** | Orchestrator | None (but event bus) |
| **Adding Steps** | Modify orchestrator | Add event handlers |
| **Testing** | Test orchestrator + services | Test event interactions |
| **Debugging** | Easier (centralized) | Harder (distributed) |

## Orchestration: Pros and Cons

### ✅ Pros

1. **Clear Flow**: One place to see entire saga logic
2. **Easier Debugging**: Centralized logging and tracing
3. **Simple Compensation**: Orchestrator controls rollback
4. **Type Safety**: Compile-time checking of step sequence

### ❌ Cons

1. **Single Point of Failure**: Orchestrator down = no sagas
2. **Tight Coupling**: Services depend on orchestrator
3. **Scaling**: Orchestrator can become bottleneck
4. **Changes**: Modifying flow requires orchestrator changes

## Choreography: Pros and Cons

### ✅ Pros

1. **Loose Coupling**: Services only know about events
2. **Scalability**: No central bottleneck
3. **Autonomy**: Teams can evolve services independently
4. **Resilience**: No single point of failure

### ❌ Cons

1. **Hard to Understand**: Flow spread across services
2. **Debugging Nightmare**: Tracing events is complex
3. **Cyclic Dependencies**: Events can create loops
4. **Compensation Complexity**: Who triggers rollback?

## When to Use Which?

### Use Orchestration When:

- Flow is relatively simple and linear
- You need clear visibility into saga state
- Compensation logic is complex
- Team is small/centralized
- Debugging simplicity is important

### Use Choreography When:

- Services are truly independent
- Teams are distributed (microservices)
- Flow is complex with many branches
- High scalability is required
- Services evolve independently

## Hybrid Approach

Many systems use **both**:

```
┌──────────────────────────────────────────────────────┐
│              Domain Boundary (Order)                 │
│                                                      │
│  ┌─────────────────────────────────────────────┐    │
│  │         Order Saga Orchestrator              │    │
│  │  ┌─────────┐ ┌─────────┐ ┌─────────┐       │    │
│  │  │Reserve  │→│ Charge  │→│ Notify  │       │    │
│  │  │Inventory│ │ Payment │ │Customer │       │    │
│  │  └─────────┘ └─────────┘ └─────────┘       │    │
│  └───────────────────┬─────────────────────────┘    │
│                      │                               │
└──────────────────────┼───────────────────────────────┘
                       │ Event
                       ▼
┌──────────────────────────────────────────────────────┐
│              Domain Boundary (Shipping)              │
│                                                      │
│  ┌─────────────────────────────────────────────┐    │
│  │        Shipping Saga Orchestrator            │    │
│  │  ┌─────────┐ ┌─────────┐ ┌─────────┐       │    │
│  │  │ Create  │→│ Assign  │→│  Track  │       │    │
│  │  │Shipment │ │ Driver  │ │ Package │       │    │
│  │  └─────────┘ └─────────┘ └─────────┘       │    │
│  └─────────────────────────────────────────────┘    │
│                                                      │
└──────────────────────────────────────────────────────┘
```

- **Within domain**: Orchestration (clear control)
- **Between domains**: Choreography (loose coupling)

## Code Example: Choreography (For Comparison)

```java
// Choreography approach - NOT used in this project, shown for comparison

// Each service listens for events and publishes next event

@Service
public class InventoryService {
    
    @KafkaListener(topics = "order.created")
    public void onOrderCreated(OrderCreatedEvent event) {
        try {
            reserveInventory(event.getItems());
            publish(new InventoryReservedEvent(event.getOrderId()));
        } catch (Exception e) {
            publish(new InventoryReservationFailedEvent(event.getOrderId()));
        }
    }
    
    @KafkaListener(topics = "payment.failed")
    public void onPaymentFailed(PaymentFailedEvent event) {
        // Compensate
        releaseInventory(event.getOrderId());
    }
}

@Service
public class PaymentService {
    
    @KafkaListener(topics = "inventory.reserved")
    public void onInventoryReserved(InventoryReservedEvent event) {
        try {
            chargePayment(event.getOrderId());
            publish(new PaymentChargedEvent(event.getOrderId()));
        } catch (Exception e) {
            publish(new PaymentFailedEvent(event.getOrderId()));
        }
    }
}
```

## This Project's Choice: Orchestration

We chose orchestration because:

1. **Learning Focus**: Easier to understand and follow
2. **Visibility**: Clear state machine visualization
3. **Compensation**: Centralized rollback logic
4. **State Tracking**: All state in one place

For production systems with many independent microservices, consider choreography or hybrid approaches.
