# Student Management System (Система управления расписанием репетитора)

Бэкенд-сервис для автоматизации работы индивидуального преподавателя: управление профилями учеников, сеткой слотов, процедурой бронирования, отмены и посещаемости занятий.

## Технологический стек
* **Java 21**, **Spring Boot 3**
* **Spring Data JPA**, **H2 Database** (In-Memory)
* **OpenAPI / Swagger UI**
* **JUnit 5**, **Mockito**, **AssertJ**

## Архитектура
Приложение спроектировано по принципам **Onion Architecture** (Чистая архитектура):
* `domain` — доменные сущности и интерфейсы репозиториев (бизнес-правила без внешних зависимостей).
* `application` — прикладной слой, содержащий 9 сервисов бизнес-логики и DTO.
* `infrastructure` — инфраструктурный слой (REST-контроллеры, JPA-сущности, адаптеры БД).

## Основные сервисы (Application Layer)
1. `StudentRegistrationService` — регистрация новых учеников.
2. `StudentAdminService` — администрирование данных студентов.
3. `StudentServiceImpl` — общее управление профилями учеников.
4. `SlotManagementService` — создание и управление временными слотами.
5. `ScheduleViewService` — просмотр и формирование расписания.
6. `BookingService` — логика бронирования занятий.
7. `CancellationService` — отмена и замена слотов.
8. `AttendanceTrackingService` — фиксация посещаемости и проведенных уроков.
9. `NotificationService` — генерация сообщений и уведомлений.

## Запуск приложения
1. Клонировать репозиторий:
   ```bash
   git clone [https://github.com/SuperPuper7565/StudentManagementSystem.git](https://github.com/SuperPuper7565/StudentManagementSystem.git)