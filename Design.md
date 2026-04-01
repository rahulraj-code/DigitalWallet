# Digital Wallet

## What
Distributed wallet transaction management system that ensures financial consistency and correctness across horizontally sharded MySQL databases through SAGA pattern implementation.

## Scope

### Functional Requirements
1. Wallet Balance Management
2. SAGA orchestration with persistent state machine
3. Financial Transaction compensation (refunds, reversals)
4. Support Complete Audit Trail

### Non-Functional Requirements
1. **Scalable** - horizontally: support 1K TPS
2. Achieve 99.9% transaction success rate.
3. Should be strictly Consistent and Correct.

---

## Components

### Service Layer (Spring Boot)
- **Wallet Service**: Handles credit/debit operations
- **Transaction Service**: Manages transfers and payments
- **Account Service**: Balance and account management

### SAGA Orchestration Layer
- **SAGA Orchestrator**: Coordinates distributed transactions
- **State Machine**: Tracks transaction execution state with pessimistic locking
- **Compensation Manager**: Handles rollback and reversals

### Data Layer
- **Apache ShardingSphere Proxy**: Routes queries based on `user_id` sharding key
- **PostgreSQL Shards (1-3)**: Horizontally partitioned wallet data
- Each shard handles a range of `user_ids` for data locality

---

## Entities
1. Accounts (users)
2. Wallets
3. Transaction

---

## Tables

### Account
| Field               | Type                     | Description                                                                 |
|---------------------|--------------------------|---------------------------------------------------------------------------|
| **Id**              | STRING (Globally Unique) | Created by hashing `name + contact_number`                                |
| **User_Name**       | STRING                   |                                                                           |
| **Email**           | STRING                   |                                                                           |
| **Contact_Number**  | number (unique)         |                                                                           |
| **Wallet_Ids**      | List of Wallet Ids       | Associated to account                                                     |
| **Created_at**      | TIMESTAMP                |                                                                           |
| **Updated_at**      | TIMESTAMP                |                                                                           |

### Wallet
| Field           | Type              | Description                                                                 |
|-----------------|-------------------|---------------------------------------------------------------------------|
| **Id**          | Globally Unique   |                                                                           |
| **TYPE**        | ENUM              |                                                                           |
| **Balance**     | BigDecimal        |                                                                           |
| **Account_id**  | -                 |                                                                           |
| **Currency**    | -                 |                                                                           |
| **Created_at**  | TIMESTAMP         |                                                                           |
| **Updated_at**  | TIMESTAMP         |                                                                           |

### Transaction
| Field               | Type                     | Description                                                                 |
|---------------------|--------------------------|---------------------------------------------------------------------------|
| **Transaction_Id**  | Globally Unique          |                                                                           |
| **Type**            | ENUM (Credit, Debit, Refund/Reversal) |                                                                       |
| **Idempotency_key** | unique                   |                                                                           |
| **Wallet_id**       | -                        |                                                                           |
| **Amount**          | -                        |                                                                           |
| **details**         | String (metadata)        |                                                                           |
| **Created_at**      | TIMESTAMP                |                                                                           |
| **Updated_at**      | TIMESTAMP                |                                                                           |

---

## Transaction Correctness Approaches

### 1. 2PC (2 Phase Commit)
- Locks all databases (corresponding rows)
- Causes increased latency due to long locks
- **Issue**: After acquiring locks, service can crash during commit phase
- Low-level solution requiring database transaction modifications

### 2. Try-Confirm/Cancel (TCC)
#### 2-Step Process:
1. Coordinator asks all databases to reserve resources
2. If all reply "yes" → confirm; otherwise → cancel

**Implementation Requirements:**
```plaintext
╔═══════════════════════════════════════════════════════════════════════╗
║  TCC (Try-Confirm-Cancel) — NEEDS frozen_balance Column               ║
╠═══════════════════════════════════════════════════════════════════════╣
║                                                                       ║
║  TRY Phase:     "Reserve the money, don't move it yet"                ║
║                  balance=1000, frozen=100 → available=900             ║
║                  🔒 Lock released after TRY                           ║
║                                                                       ║
║  CONFIRM Phase: "Okay, actually move it now"                          ║
║                  balance=900, frozen=0                                ║
║                                                                       ║
║  CANCEL Phase:  "Never mind, release the reservation"                 ║
║                  balance=1000, frozen=0                               ║
║                                                                       ║
║  ⚠️  TWO separate database transactions for one logical operation     ║
║  ⚠️  Complex state management (frozen vs available)                   ║
║  ⚠️  Stale freeze cleanup needed if coordinator crashes               ║
╚═══════════════════════════════════════════════════════════════════════╝
```

### 3. SAGA
1. All the operation are ordered in a certain **sequence** and each operation is an **Independent Transaction**
2. Operations are executed in order and if any operation fails -> entire process is rolled back in reverse order.
- fundamental TradeOff : There is a brief Inconsistency window during in-flight transaction. 
- Thus it does not guarantees Strong consistency but give eventual Consistency