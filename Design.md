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


Plant UML :
```
@startuml
!theme plain
skinparam linetype ortho
skinparam roundcorner 10
skinparam class {
    BackgroundColor #FEFEFE
    BorderColor #2C3E50
    ArrowColor #2C3E50
    HeaderBackgroundColor #3498DB
    HeaderFontColor #FFFFFF
    FontSize 13
    AttributeFontSize 12
}

skinparam note {
    BackgroundColor #FFF9C4
    BorderColor #F9A825
}

title **Distributed Wallet Application - Entity Relationship Diagram**\n<size:14>SAGA-Based | Horizontally Sharded | Snowflake IDs</size>

' ============================================================
' ENUMS
' ============================================================

enum WalletType <<ENUM>> #E8F5E9 {
    SAVINGS
    CURRENT
    ESCROW
    REWARD
}

enum TransactionType <<ENUM>> #E8F5E9 {
    CREDIT
    DEBIT
    REFUND
    REVERSAL
}

enum WalletStatus <<ENUM>> #E8F5E9 {
    ACTIVE
    FROZEN
    CLOSED
}

enum AccountStatus <<ENUM>> #E8F5E9 {
    ACTIVE
    SUSPENDED
    DEACTIVATED
}

enum SagaStatus <<ENUM>> #E8F5E9 {
    STARTED
    RUNNING
    COMPENSATING
    COMPLETED
    FAILED
    COMPENSATED
    POISON
}

enum StepStatus <<ENUM>> #E8F5E9 {
    PENDING
    EXECUTING
    COMPLETED
    FAILED
    COMPENSATED
    SKIPPED
}

' ============================================================
' ACCOUNT ENTITY
' ============================================================

entity "**ACCOUNT**" as account #ECF0F1 {
    <&key> <b>id</b> : VARCHAR(64) <<PK>> <<NOT NULL>>
    --
    <i>SHA-256(user_name + contact_number)</i>
    <i>Globally Unique — Deterministic</i>
    ==
    <&person> user_name : VARCHAR(128) <<NOT NULL>>
    <&envelope-closed> email : VARCHAR(256) <<NOT NULL>> <<UNIQUE>>
    <&phone> contact_number : VARCHAR(15) <<NOT NULL>> <<UNIQUE>>
    <&shield> status : AccountStatus <<NOT NULL>> <<DEFAULT ACTIVE>>
    --
    <&calendar> created_at : TIMESTAMPTZ <<NOT NULL>> <<DEFAULT NOW()>>
    <&calendar> updated_at : TIMESTAMPTZ <<NOT NULL>> <<DEFAULT NOW()>>
}

note top of account
  **Sharding Key:** id
  **ID Generation:** SHA-256(user_name + contact_number)
  
  Deterministic ID ensures:
  • Same user always maps to same shard
  • No duplicate accounts for same person
  • Natural idempotency on creation
end note

' ============================================================
' WALLET ENTITY
' ============================================================

entity "**WALLET**" as wallet #ECF0F1 {
    <&key> <b>id</b> : BIGINT <<PK>> <<NOT NULL>>
    --
    <i>Snowflake ID — Globally Unique</i>
    <i>Encodes: timestamp + datacenter + machine + seq</i>
    ==
    <&spreadsheet> type : WalletType <<NOT NULL>>
    <&dollar> balance : DECIMAL(20,8) <<NOT NULL>> <<DEFAULT 0.00>>
    <&link-intact> account_id : VARCHAR(64) <<FK>> <<NOT NULL>>
    <&globe> currency : VARCHAR(10) <<NOT NULL>> <<DEFAULT 'INR'>>
    <&shield> status : WalletStatus <<NOT NULL>> <<DEFAULT ACTIVE>>
    --
    <&calendar> created_at : TIMESTAMPTZ <<NOT NULL>> <<DEFAULT NOW()>>
    <&calendar> updated_at : TIMESTAMPTZ <<NOT NULL>> <<DEFAULT NOW()>>
    ==
    <b>CONSTRAINTS:</b>
    CHECK (balance >= 0)
    UNIQUE (account_id, currency, type)
}

note right of wallet
  **Sharding Key:** account_id
  
  **Locking Strategy:**
  • Pessimistic: SELECT ... FOR UPDATE
  • Lock held only during SAGA step transaction
  • Lock ordering: ascending account_id
    to prevent deadlocks
  
  **No frozen_balance:**
  SAGA pattern debits/credits immediately.
  Compensation reverses on failure.
end note

' ============================================================
' TRANSACTION ENTITY (IMMUTABLE LEDGER)
' ============================================================

entity "**TRANSACTION**" as transaction #ECF0F1 {
    <&key> <b>transaction_id</b> : BIGINT <<PK>> <<NOT NULL>>
    --
    <i>Snowflake ID — Globally Unique</i>
    ==
    <&spreadsheet> type : TransactionType <<NOT NULL>>
    <&link-intact> wallet_id : BIGINT <<FK>> <<NOT NULL>>
    <&link-intact> account_id : VARCHAR(64) <<NOT NULL>>
    <&dollar> amount : DECIMAL(20,8) <<NOT NULL>>
    <&link-intact> saga_id : BIGINT <<NOT NULL>>
    <&text> idempotency_key : VARCHAR(128) <<NOT NULL>> <<UNIQUE>>
    <&info> details : JSONB <<NULLABLE>>
    --
    <&calendar> created_at : TIMESTAMPTZ <<NOT NULL>> <<DEFAULT NOW()>>
    ==
    <b>CONSTRAINTS:</b>
    CHECK (amount > 0)
    <b>IMMUTABLE</b> (no UPDATE/DELETE allowed)
}

note left of transaction
  **Sharding Key:** account_id
  
  **Truly Immutable Ledger:**
  • INSERT only — triggers block UPDATE/DELETE
  • No status column (record exists = completed)
  • No updated_at (never modified after creation)
  • REFUND/REVERSAL creates NEW record
  
  **No balance_before/after:**
  • amount + type = complete information
  • Balance at any point = SUM(credits) - SUM(debits)
  • Avoids internal contradiction risk
  • Leaner writes at high TPS
  
  **No reference_txn_id:**
  • saga_id groups all related transactions
  • idempotency_key encodes step relationship
  • FORWARD/COMPENSATE matched by step_name
  • Cross-shard safe (no FK needed)
  
  **idempotency_key** format:
  {saga_id}:{step_name}:{direction}
  
  **Reconciliation:**
  wallet.balance = SUM(credits) - SUM(debits)
  
  **details** (JSONB) example:
  {
    "counterparty_wallet_id": 12345,
    "counterparty_account_id": "abc..",
    "reason": "P2P Transfer",
    "initiated_by": "API",
    "original_saga_id": 5501
  }
end note

' ============================================================
' SAGA INSTANCE (Persistent State Machine)
' ============================================================

entity "**SAGA_INSTANCE**" as saga #F5F5F5 {
    <&key> <b>id</b> : BIGINT <<PK>> <<NOT NULL>>
    --
    <i>Snowflake ID = saga_id</i>
    ==
    <&spreadsheet> saga_type : VARCHAR(64) <<NOT NULL>>
    <&shield> status : SagaStatus <<NOT NULL>> <<DEFAULT STARTED>>
    <&spreadsheet> current_step : INT <<NOT NULL>> <<DEFAULT 0>>
    <&spreadsheet> total_steps : INT <<NOT NULL>>
    <&info> payload : JSONB <<NOT NULL>>
    <&person> owner_instance_id : VARCHAR(64) <<NOT NULL>>
    <&calendar> last_heartbeat : TIMESTAMPTZ <<NOT NULL>>
    <&spreadsheet> retry_count : INT <<NOT NULL>> <<DEFAULT 0>>
    <&spreadsheet> max_retries : INT <<NOT NULL>> <<DEFAULT 3>>
    <&info> error_message : TEXT <<NULLABLE>>
    <&link-intact> initiated_by : VARCHAR(64) <<NOT NULL>>
    <&calendar> expires_at : TIMESTAMPTZ <<NOT NULL>>
    --
    <&calendar> created_at : TIMESTAMPTZ <<NOT NULL>> <<DEFAULT NOW()>>
    <&calendar> updated_at : TIMESTAMPTZ <<NOT NULL>> <<DEFAULT NOW()>>
}

note right of saga
  **Sharding Key:** initiated_by (account_id)
  
  **State Transitions:**
  STARTED → RUNNING → COMPLETED
                │
                ▼ (step failure)
             FAILED → COMPENSATING → COMPENSATED
                          │
                          ▼ (compensation fails)
                        POISON (manual intervention)
  
  **Recovery:** Stale heartbeat detection
  allows another instance to claim and resume
end note

' ============================================================
' SAGA STEP EXECUTION
' ============================================================

entity "**SAGA_STEP_EXECUTION**" as saga_step #F5F5F5 {
    <&key> <b>id</b> : BIGINT <<PK>> <<NOT NULL>>
    --
    <i>Snowflake ID</i>
    ==
    <&link-intact> saga_id : BIGINT <<FK>> <<NOT NULL>>
    <&spreadsheet> step_index : INT <<NOT NULL>>
    <&spreadsheet> step_name : VARCHAR(64) <<NOT NULL>>
    <&spreadsheet> step_type : VARCHAR(20) <<NOT NULL>>
    <&shield> status : StepStatus <<NOT NULL>> <<DEFAULT PENDING>>
    <&info> input_data : JSONB <<NULLABLE>>
    <&info> output_data : JSONB <<NULLABLE>>
    <&info> error_detail : TEXT <<NULLABLE>>
    <&link-intact> initiated_by : VARCHAR(64) <<NOT NULL>>
    --
    <&calendar> started_at : TIMESTAMPTZ <<NULLABLE>>
    <&calendar> completed_at : TIMESTAMPTZ <<NULLABLE>>
    <&calendar> created_at : TIMESTAMPTZ <<NOT NULL>> <<DEFAULT NOW()>>
    ==
    <b>CONSTRAINTS:</b>
    UNIQUE (saga_id, step_index, step_type)
    CHECK step_type IN ('FORWARD', 'COMPENSATE')
}

note left of saga_step
  **Sharding Key:** initiated_by (account_id)
  
  **step_type values:**
  • FORWARD — normal execution
  • COMPENSATE — reversal execution
  
  **Colocated with saga_instance**
  on same shard (same initiated_by)
  for local JOINs
end note

' ============================================================
' RELATIONSHIPS
' ============================================================

account     ||--|{  wallet      : "owns\n(1 account → N wallets)"
wallet      ||--|{  transaction : "records\n(1 wallet → N transactions)"
saga        ||--|{  saga_step   : "contains\n(1 saga → N steps)"
saga        ||..|{  transaction : "produces\n(1 saga → N transactions)"

' ============================================================
' SHARD COLOCATION
' ============================================================

note as colocation #E3F2FD
  **Shard Colocation Guarantee**
  ════════════════════════════
  
  All entities for one account live on the **SAME shard**:
  
  Account ──┐
  Wallets ──┤ sharded by account_id
  Txns    ──┤ (same value across all tables)
  SAGAs   ──┘
  
  This enables:
  ✅ Local JOINs within a shard
  ✅ Single-shard ACID transactions
  ✅ No distributed reads for single-user queries
  
  Cross-user transfers touch 2 shards → SAGA required
end note

' ============================================================
' LEGEND
' ============================================================

legend bottom right
    |= Symbol |= Meaning |
    | <&key> | Primary Key |
    | <&link-intact> | Foreign Key |
    | <<PK>> | Primary Key Constraint |
    | <<FK>> | Foreign Key Reference |
    | <<UNIQUE>> | Unique Constraint |
    | <<NOT NULL>> | Not Nullable |
    |  JSONB  | PostgreSQL JSON Binary |
    
    **Sharding Strategy:** MOD(hash(account_id), shard_count)
    **ID Generation:** Snowflake (Wallet, Transaction, SAGA) / SHA-256 (Account)
    **Database:** PostgreSQL 16 via ShardingSphere Proxy
    **Locking:** Pessimistic (SELECT FOR UPDATE) per SAGA step
endlegend
@enduml
```