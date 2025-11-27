```mermaid
classDiagram
  class User {
    +Long id
    +String name
    +String email
    +String phone
    +String passwordHash
    +Boolean isAdmin
    +Boolean isActive
    +BigDecimal balance
    +BigDecimal totalSpent
  }

  class Media {
    +Long id
    +String title
    +String description
    +ProductType productType
    +BigDecimal price
    +Double rating
    +Option~Long~ categoryId
    +String assetPath
    +Int stock
    +Option~Long~ promotionId
    +Boolean isActive
  }

  class Category {
    +Long id
    +String name
    +Option~Long~ parentId
    +String description
    +String productType
  }

  class CartEntry {
    +Long userId
    +Long mediaId
    +Int quantity
  }

  class Order {
    +Long id
    +Long userId
    +Vector~OrderItem~ items
    +BigDecimal totalGross
    +BigDecimal totalDiscount
    +BigDecimal totalNet
  }

  class Transaction {
    +Long id
    +TransactionType transactionType
    +Option~Long~ fromUserId
    +Option~Long~ toUserId
    +Option~Long~ mediaId
    +Int quantity
    +BigDecimal grossAmount
    +BigDecimal discount
    +BigDecimal netAmount
  }

  class Download {
    +Long id
    +Long userId
    +Long mediaId
    +Int quantity
    +BigDecimal price
    +BigDecimal discount
    +BigDecimal finalPrice
  }

  class BalanceRequest {
    +Long id
    +Long userId
    +BigDecimal amount
    +String paymentMethod
    +RequestStatus status
  }

  class TopUp {
    +Long id
    +Long userId
    +Long adminId
    +BigDecimal amount
  }

  class Receipt {
    +Long id
    +Long orderId
    +Long userId
    +String series
    +String number
    +String qrData
  }

  class PasswordResetRequest {
    +Long id
    +Long userId
    +PasswordResetStatus status
  }

  User "1" --> "*" Order
  User "1" --> "*" Download
  User "1" --> "*" CartEntry
  User "1" --> "*" BalanceRequest
  User "1" --> "*" PasswordResetRequest
  Order "1" --> "*" OrderItem
  Order "1" --> "1" Receipt
  Media "1" --> "*" Download
  Media "1" --> "*" CartEntry
  Category "1" --> "*" Media
```
