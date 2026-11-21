import java.time.LocalDate; 
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class {
    private static final int MINIMUM_RENTAL_AGE = 18;
    private static final double LATE_FEE_MULTIPLIER = 1.0; // 100% of daily rate per late day
    private static final String CONTACT_REGEX = "\\d{11}";
    private static final String LICENSE_REGEX = "[A-Za-z]-\\d{6}-\\d{4}";

    private static final List<Vehicle> fleet = new ArrayList<>();
    private static final List<RentalRecord> rentals = new ArrayList<>();
    private static int nextBookingNumber = 1001;
    private static int nextPaymentNumber = 5001;

    public static void main(String[] args) {
        seedVehicles();
        Scanner scanner = new Scanner(System.in);
        boolean running = true;
// OPTIONS MENU LOOP
        while (running) {
            System.out.println("\n=== Car Rental Management System ===");
            System.out.println("1. View Available Vehicles");
            System.out.println("2. Book a Vehicle");
            System.out.println("3. Return a Vehicle");
            System.out.println("4. View Booking History");
            System.out.println("0. Exit");
            System.out.print("Choose an option: ");

            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1" -> viewVehicles(scanner);
                case "2" -> bookVehicle(scanner);
                case "3" -> returnVehicle(scanner);
                case "4" -> viewBookingHistory(scanner);
                case "0" -> {
                    running = false;
                    System.out.println("Goodbye!");
                }
                default -> System.out.println("Invalid optiCarRentalManagementSystem on. Please try again.");
            }
        }

        scanner.close();
    }

    private static String formatMoney(double amount) {
        if (Math.abs(amount - Math.rint(amount)) < 1e-9) {
            return String.format("PHP %,.0f", Math.rint(amount));
        }
        return String.format("PHP %,.2f", amount);
    }

    private static void seedVehicles() {
        fleet.add(new Vehicle("V01", "Toyota", "Vios", "Sedan", 2500));
        fleet.add(new Vehicle("V02", "Toyota", "Innova", "MPV", 3700));
        fleet.add(new Vehicle("V03", "Toyota", "Fortuner", "SUV", 4300));
        fleet.add(new Vehicle("V04", "Toyota", "Hiace Commuter", "Van", 3500));
        fleet.add(new Vehicle("V05", "Mitsubishi", "Mirage G4", "Sedan", 2150));
        fleet.add(new Vehicle("V06", "Mitsubishi", "Xpander", "MPV", 3000));
        fleet.add(new Vehicle("V07", "Mitsubishi", "Montero Sport", "SUV", 3600));
    }

    private static void viewVehicles(Scanner scanner) {
        System.out.println("\n--- View Vehicles ---");
        System.out.println("a. All Vehicles");
        System.out.println("b. Filter by Brand (e.g., Toyota, Mitsubishi)");
        System.out.println("c. Filter by Type (e.g., Sedan, MPV, SUV, Van)");
        System.out.print("Select filter: ");
        String filter = scanner.nextLine().trim().toLowerCase();

        if (filter.equals("a")) {
            displayVehicles(fleet, "No vehicles in the system.");
        } else if (filter.equals("b")) {
            System.out.print("Enter brand: ");
            String brand = scanner.nextLine().trim();
            displayByBrand(brand);
        } else if (filter.equals("c")) {
            System.out.print("Enter type: ");
            String type = scanner.nextLine().trim();
            displayByType(type);
        } else {
            System.out.println("Invalid filter option.");
        }
    }

    private static void displayByBrand(String brand) {
        List<Vehicle> matches = new ArrayList<>();
        for (Vehicle v : fleet) {
            if (v.getBrand().equalsIgnoreCase(brand)) {
                matches.add(v);
            }
        }
        displayVehicles(matches, "No vehicles found for brand: " + brand);
    }

    private static void displayByType(String type) {
        List<Vehicle> matches = new ArrayList<>();
        for (Vehicle v : fleet) {
            if (v.getType().equalsIgnoreCase(type)) {
                matches.add(v);
            }
        }
        displayVehicles(matches, "No vehicles found for type: " + type);
    }

    private static void bookVehicle(Scanner scanner) {
        displayVehicles(fleet, "\nNo vehicles in the system.");
        if (fleet.isEmpty()) {
            return;
        }

        System.out.println("\n=== RESERVATION FORM ===");
        System.out.print("Enter Vehicle ID to book (or 'c' to cancel): ");
        String id = scanner.nextLine().trim();
        if (id.equalsIgnoreCase("c")) {
            System.out.println("Reservation cancelled.");
            return;
        }

        Vehicle selected = findVehicleById(id);
        if (selected == null) {
            System.out.println("Error: Vehicle ID not found.");
            return;
        }
        if (!selected.isAvailable()) {
            System.out.printf("Error: %s %s (%s) is currently %s (Not Available) and cannot be booked.%n",
                    selected.getBrand(), selected.getModel(), selected.getId(), selected.getStatus());
            return;
        }

        System.out.println("-- Renter Details (Required, cannot be skipped) --");
        String fullName = readRequiredNonEmptyString(scanner, "Customer Full Name: ");
        if (fullName == null) {
            return;
        }

        int age = readCustomerAge(scanner, "Customer Age (Must be " + MINIMUM_RENTAL_AGE + "+): ");
        if (age < MINIMUM_RENTAL_AGE) {
            return;
        }

        String contactNumber = readElevenDigitContact(scanner, "Contact Number (11 digits, e.g., 09123456789): ");
        if (contactNumber == null) {
            return;
        }

        String licenseNo = readDriverLicense(scanner, "Driver's License No. (Format L-YYMMDD-XXXX, e.g., N-012345-6789): ");
        if (licenseNo == null) {
            return;
        }

        System.out.println("-- Rental Schedule & Deposit --");
        LocalDate today = LocalDate.now();
        LocalDate pickUpDate = readFutureOrTodayDate(
                scanner,
                "Enter Pick-up Date (YYYY-MM-DD, today or later, e.g., " + today + "): ",
                today
        );
        if (pickUpDate == null) {
            return;
        }

        LocalDate dropOffDate = readDropOffDate(
                scanner,
                "Enter Drop-off Date (YYYY-MM-DD, after " + pickUpDate + "): ",
                pickUpDate
        );
        if (dropOffDate == null) {
            return;
        }

        int days = (int) ChronoUnit.DAYS.between(pickUpDate, dropOffDate);
        System.out.println("Calculated Rental Duration: " + days + " day(s)");

        double deposit = readNonNegativeDouble(scanner, "Enter optional security deposit (0 for none): ");
        if (deposit < 0) {
            return;
        }

        double totalAmount = (selected.getDailyRate() * days) + deposit;
        System.out.println("Total Amount Due: " + formatMoney(totalAmount));

        String method;
        double amountTendered;

        while (true) {
            System.out.println("\n--- Payment Option ---");
            System.out.println("1. Pay now (Full, Partial, or 0 for Pending)");
            System.out.println("2. Leave payment PENDING / Unpaid (must pay upon return)");
            System.out.print("Choose payment option: ");
            String payOption = scanner.nextLine().trim();

            if (payOption.equals("2")) {
                method = "Pending / Unpaid";
                amountTendered = 0.0;
                break;
            } else if (payOption.equals("1")) {
                method = promptPaymentMethod(scanner);
                if (method == null) {
                    return;
                }
                amountTendered = readValidPaymentAmount(scanner, totalAmount, true);
                if (amountTendered < 0) {
                    return;
                }
                if (amountTendered == 0.0) {
                    method = "Pending / Unpaid";
                }
                break;
            } else {
                System.out.println("Error: Please enter 1 or 2.");
            }
        }

        boolean rentedAheadOfTime = pickUpDate.isAfter(today);
        if (rentedAheadOfTime) {
            selected.markReserved();
        } else {
            selected.markNotAvailable();
        }

        Payment payment = new Payment("PAY-" + (nextPaymentNumber++), method, totalAmount, amountTendered);
        Customer customer = new Customer(fullName, age, contactNumber, licenseNo);
        String bookingId = "BKG-" + (nextBookingNumber++);

        RentalRecord record = new RentalRecord(
                bookingId,
                customer,
                selected,
                pickUpDate,
                dropOffDate,
                days,
                selected.getDailyRate(),
                deposit,
                payment,
                rentedAheadOfTime
        );

        rentals.add(record);
        record.printReceipt();

        if (rentedAheadOfTime) {
            System.out.printf("Status Update: %s %s (%s) is now marked as Reserved (Not Available) for pick-up on %s.%n",
                    selected.getBrand(), selected.getModel(), selected.getId(), pickUpDate);
        } else {
            System.out.printf("Status Update: %s %s (%s) is now marked as Not Available.%n",
                    selected.getBrand(), selected.getModel(), selected.getId());
        }
    }

    private static void returnVehicle(Scanner scanner) {
        displayActiveRentals();

        boolean hasActive = false;
        for (RentalRecord r : rentals) {
            if (r.isActive()) {
                hasActive = true;
                break;
            }
        }

        if (!hasActive) {
            return;
        }

        System.out.print("Enter Vehicle ID to return (or 'c' to cancel): ");
        String id = scanner.nextLine().trim();
        if (id.equalsIgnoreCase("c")) {
            System.out.println("Return cancelled.");
            return;
        }

        Vehicle selected = findVehicleById(id);
        if (selected == null) {
            System.out.println("Error: Vehicle not found.");
            return;
        }
        if (selected.isAvailable()) {
            System.out.println("Error: Vehicle is already Available.");
            return;
        }

        RentalRecord activeRecord = findActiveRentalByVehicleId(selected.getId());
        if (activeRecord != null) {
            LocalDate returnDate = readReturnDate(
                    scanner,
                    "Enter Date Returned (YYYY-MM-DD, scheduled drop-off: " + activeRecord.getDropOffDate() + "): ",
                    activeRecord.getPickUpDate()
            );
            if (returnDate == null) {
                return;
            }

            long lateDays = 0;
            double lateCharge = 0.0;
            if (returnDate.isAfter(activeRecord.getDropOffDate())) {
                lateDays = ChronoUnit.DAYS.between(activeRecord.getDropOffDate(), returnDate);
                lateCharge = lateDays * activeRecord.getDailyRate() * LATE_FEE_MULTIPLIER;
                activeRecord.applyLateReturn(returnDate, (int) lateDays, lateCharge);
                System.out.println("Late Return Detected! Late by " + lateDays + " day(s) | Additional Late Charge: "
                        + formatMoney(lateCharge));
            } else {
                activeRecord.applyLateReturn(returnDate, 0, 0.0);
                System.out.println("Returned on or before scheduled drop-off date. No late charges.");
            }

            Payment payment = activeRecord.getPayment();
            if (payment.getBalanceDue() > 0) {
                System.out.println("Payment Status is " + payment.getPaymentStatus()
                        + ". Remaining balance to pay upon return for " + activeRecord.getBookingId()
                        + " (" + activeRecord.getCustomer().getFullName() + "): "
                        + formatMoney(payment.getBalanceDue()));
                System.out.println("Payment is required before completing vehicle return.");

                String method = promptPaymentMethod(scanner);
                if (method == null) {
                    System.out.println("Return incomplete: Payment is required for unpaid/remaining balances.");
                    return;
                }

                double amountTendered = readValidPaymentAmount(scanner, payment.getBalanceDue(), false);
                if (amountTendered < 0) {
                    System.out.println("Return incomplete: Payment is required for unpaid/remaining balances.");
                    return;
                }

                double change = payment.recordPayment(amountTendered, method);
                System.out.println("Payment settled! Change: " + formatMoney(change)
                        + " | Remaining balance: " + formatMoney(payment.getBalanceDue())
                        + " (Status: " + payment.getPaymentStatus() + ")");
            }

            activeRecord.markReturned();
        }

        selected.markAvailable();
        System.out.printf("Vehicle %s (%s %s) returned and is now marked as Available.%n",
                selected.getId(), selected.getBrand(), selected.getModel());
    }

    private static LocalDate readReturnDate(Scanner scanner, String prompt, LocalDate pickUpDate) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            if (input.equalsIgnoreCase("c")) {
                System.out.println("Return cancelled.");
                return null;
            }
            if (input.isEmpty()) {
                System.out.println("Error: Date returned is required. Use YYYY-MM-DD or 'c' to cancel.");
                continue;
            }
            try {
                LocalDate returnDate = LocalDate.parse(input);
                if (returnDate.isBefore(pickUpDate)) {
                    System.out.println("Error: Return date cannot be earlier than pick-up date (" + pickUpDate
                            + "). Enter 'c' to cancel.");
                    continue;
                }
                return returnDate;
            } catch (DateTimeParseException e) {
                System.out.println("Error: Invalid date format. Use YYYY-MM-DD (e.g., 2026-11-25) or 'c' to cancel.");
            }
        }
    }

    private static String readRequiredNonEmptyString(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            if (input.equalsIgnoreCase("c")) {
                System.out.println("Reservation cancelled.");
                return null;
            }
            if (input.isEmpty()) {
                System.out.println("Error: This field is required and cannot be skipped or left blank. Enter 'c' to cancel.");
                continue;
            }
            return input;
        }
    }

    private static String readElevenDigitContact(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            if (input.equalsIgnoreCase("c")) {
                System.out.println("Reservation cancelled.");
                return null;
            }
            if (input.isEmpty()) {
                System.out.println("Error: Contact number is required and cannot be skipped. Enter 11 digits or 'c' to cancel.");
                continue;
            }
            if (!input.matches(CONTACT_REGEX)) {
                System.out.println("Error: Contact number must be exactly 11 digits (0-9 only, no spaces or letters). Enter 'c' to cancel.");
                continue;
            }
            return input;
        }
    }

    private static String readDriverLicense(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            if (input.equalsIgnoreCase("c")) {
                System.out.println("Reservation cancelled.");
                return null;
            }
            if (input.isEmpty()) {
                System.out.println("Error: Driver's license number is required and cannot be skipped. Format: L-YYMMDD-XXXX or 'c' to cancel.");
                continue;
            }
            if (!input.matches(LICENSE_REGEX)) {
                System.out.println("Error: Invalid format! Driver's license must be 1 letter and 10 digits separated by hyphens in format L-YYMMDD-XXXX (e.g., N-012345-6789).");
                continue;
            }
            return input.substring(0, 1).toUpperCase() + input.substring(1);
        }
    }

    private static void viewBookingHistory(Scanner scanner) {
        if (rentals.isEmpty()) {
            System.out.println("\nNo booking history records yet.");
            return;
        }

        System.out.println("\n--- View Booking History ---");
        System.out.println("a. All Booking History");
        System.out.println("b. Search User's Booking History (by Name or License No.)");
        System.out.print("Select option: ");
        String option = scanner.nextLine().trim().toLowerCase();

        List<RentalRecord> matched = new ArrayList<>();
        String title = "=== All Booking History ===";

        if (option.equals("a")) {
            matched.addAll(rentals);
        } else if (option.equals("b")) {
            System.out.print("Enter Customer Name or Driver's License No.: ");
            String query = scanner.nextLine().trim().toLowerCase();
            if (query.isEmpty()) {
                System.out.println("Error: Search query cannot be empty.");
                return;
            }
            title = "=== Booking History for \"" + query + "\" ===";
            for (RentalRecord r : rentals) {
                Customer c = r.getCustomer();
                if (c.getFullName().toLowerCase().contains(query)
                        || c.getDriverLicenseNo().toLowerCase().contains(query)) {
                    matched.add(r);
                }
            }
        } else {
            System.out.println("Invalid option.");
            return;
        }

        System.out.println("\n" + title);
        if (matched.isEmpty()) {
            System.out.println("No booking records found for that user.");
            return;
        }

        System.out.println("----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------");
        System.out.printf("%-10s %-15s %-4s %-15s %-11s %-11s %-11s %-10s %-12s %-10s %-9s%n",
                "Booking", "Customer", "Age", "Vehicle", "Pick-up", "Drop-off", "Returned", "Late Fee", "Total", "Pay Stat", "Rental");
        System.out.println("----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------");

        for (RentalRecord r : matched) {
            Customer c = r.getCustomer();
            Vehicle v = r.getVehicle();
            Payment p = r.getPayment();
            String returnedText = (r.getActualReturnDate() != null) ? r.getActualReturnDate().toString() : "-";
            String rentalState = r.isActive() ? "Active" : "Returned";
            System.out.printf("%-10s %-15s %-4d %-15s %-11s %-11s %-11s %-10s %-12s %-10s %-9s%n",
                    r.getBookingId(),
                    c.getFullName(),
                    c.getAge(),
                    v.getBrand() + " " + v.getModel(),
                    r.getPickUpDate(),
                    r.getDropOffDate(),
                    returnedText,
                    formatMoney(r.getLateCharge()),
                    formatMoney(r.getTotalCost()),
                    p.getPaymentStatus(),
                    rentalState);
        }
        System.out.println("----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------");
    }

    private static int readCustomerAge(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            if (input.equalsIgnoreCase("c")) {
                System.out.println("Reservation cancelled.");
                return -1;
            }
            if (input.isEmpty()) {
                System.out.println("Error: Age is required and cannot be skipped. Enter a whole number (e.g., 21) or 'c' to cancel.");
                continue;
            }
            try {
                int age = Integer.parseInt(input);
                if (age <= 0 || age > 120) {
                    System.out.println("Error: Please enter a valid age between 1 and 120, or 'c' to cancel.");
                    continue;
                }
                if (age < MINIMUM_RENTAL_AGE) {
                    System.out.println("Error: Customer must be at least " + MINIMUM_RENTAL_AGE
                            + " years old to rent a vehicle. Enter 'c' to cancel.");
                    continue;
                }
                return age;
            } catch (NumberFormatException e) {
                System.out.println("Error: Invalid age. Please enter a whole number (e.g., 21), or 'c' to cancel.");
            }
        }
    }

    private static LocalDate readFutureOrTodayDate(Scanner scanner, String prompt, LocalDate minDate) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            if (input.equalsIgnoreCase("c")) {
                System.out.println("Booking cancelled.");
                return null;
            }
            try {
                LocalDate date = LocalDate.parse(input);
                if (date.isBefore(minDate)) {
                    System.out.println("Error: Date cannot be earlier than " + minDate + ". Enter 'c' to cancel.");
                    continue;
                }
                return date;
            } catch (DateTimeParseException e) {
                System.out.println("Error: Invalid date format. Use YYYY-MM-DD (e.g., 2026-11-25) or 'c' to cancel.");
            }
        }
    }

    private static LocalDate readDropOffDate(Scanner scanner, String prompt, LocalDate pickUpDate) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            if (input.equalsIgnoreCase("c")) {
                System.out.println("Booking cancelled.");
                return null;
            }
            try {
                LocalDate dropOff = LocalDate.parse(input);
                if (!dropOff.isAfter(pickUpDate)) {
                    System.out.println("Error: Drop-off date must be after pick-up date (" + pickUpDate
                            + ") for at least 1 rental day. Enter 'c' to cancel.");
                    continue;
                }
                return dropOff;
            } catch (DateTimeParseException e) {
                System.out.println("Error: Invalid date format. Use YYYY-MM-DD (e.g., 2026-11-27) or 'c' to cancel.");
            }
        }
    }

    private static double readValidPaymentAmount(Scanner scanner, double requiredAmount, boolean allowPartialOrZero) {
        while (true) {
            String prompt = allowPartialOrZero
                    ? "Enter payment amount (0 for PENDING, partial allowed, Total: " + formatMoney(requiredAmount) + ", or 'c' to cancel): "
                    : "Enter payment amount to settle balance (Required: " + formatMoney(requiredAmount) + ", or 'c' to cancel): ";
            System.out.print(prompt);
            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("c")) {
                System.out.println("Payment cancelled.");
                return -1.0;
            }

            if (input.isEmpty()) {
                System.out.println("Error: Payment amount cannot be empty. Enter 0 for PENDING or a valid number.");
                continue;
            }

            double amount;
            try {
                amount = Double.parseDouble(input);
            } catch (NumberFormatException e) {
                System.out.println("Error: Invalid input. Enter a numeric amount (e.g., 0, 1500, or 2500).");
                continue;
            }

            if (Double.isNaN(amount) || Double.isInfinite(amount) || amount < 0) {
                System.out.println("Error: Payment amount cannot be negative.");
                continue;
            }

            if (!allowPartialOrZero && amount < requiredAmount) {
                double shortage = requiredAmount - amount;
                System.out.println("Error: Insufficient payment! You entered " + formatMoney(amount)
                        + ", which is short by " + formatMoney(shortage) + ".");
                continue;
            }

            return amount;
        }
    }

    private static double readNonNegativeDouble(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                return 0.0;
            }
            if (input.equalsIgnoreCase("c")) {
                System.out.println("Operation cancelled.");
                return -1.0;
            }
            try {
                double value = Double.parseDouble(input);
                if (Double.isNaN(value) || Double.isInfinite(value) || value < 0) {
                    System.out.println("Error: Amount cannot be negative. Enter 'c' to cancel.");
                    continue;
                }
                return value;
            } catch (NumberFormatException e) {
                System.out.println("Error: Please enter a valid number. Enter 'c' to cancel.");
            }
        }
    }

    private static String promptPaymentMethod(Scanner scanner) {
        while (true) {
            System.out.println("\n--- Select Payment Method ---");
            System.out.println("1. Cash");
            System.out.println("2. GCash");
            System.out.println("3. Bank Transfer");
            System.out.println("4. Card");
            System.out.println("0. Cancel");
            System.out.print("Choose payment method: ");

            switch (scanner.nextLine().trim()) {
                case "1" -> { return "Cash"; }
                case "2" -> { return "GCash"; }
                case "3" -> { return "Bank Transfer"; }
                case "4" -> { return "Card"; }
                case "0" -> {
                    System.out.println("Payment cancelled.");
                    return null;
                }
                default -> System.out.println("Error: Invalid payment method. Please choose 1-4 or 0 to cancel.");
            }
        }
    }

    private static void displayActiveRentals() {
        List<RentalRecord> activeList = new ArrayList<>();
        for (RentalRecord r : rentals) {
            if (r.isActive()) {
                activeList.add(r);
            }
        }

        System.out.println("\n=== Active Rentals (Reserved / Not Available Vehicles) ===");
        if (activeList.isEmpty()) {
            System.out.println("No vehicles are currently Reserved or Not Available.");
            return;
        }

        System.out.println("--------------------------------------------------------------------------------------------------------------------------------------------------");
        System.out.printf("%-10s %-7s %-15s %-15s %-11s %-11s %-5s %-11s %-12s %-12s %-9s%n",
                "Booking", "Veh ID", "Customer", "Vehicle", "Pick-up", "Drop-off", "Days", "Veh Stat", "Paid", "Balance", "Pay Stat");
        System.out.println("--------------------------------------------------------------------------------------------------------------------------------------------------");

        for (RentalRecord r : activeList) {
            Vehicle v = r.getVehicle();
            Payment p = r.getPayment();
            System.out.printf("%-10s %-7s %-15s %-15s %-11s %-11s %-5d %-11s %-12s %-12s %-9s%n",
                    r.getBookingId(),
                    v.getId(),
                    r.getCustomer().getFullName(),
                    v.getBrand() + " " + v.getModel(),
                    r.getPickUpDate(),
                    r.getDropOffDate(),
                    r.getDays(),
                    v.getStatus(),
                    formatMoney(p.getAmountPaid()),
                    formatMoney(p.getBalanceDue()),
                    p.getPaymentStatus());
        }
        System.out.println("--------------------------------------------------------------------------------------------------------------------------------------------------");
    }

    private static Vehicle findVehicleById(String id) {
        for (Vehicle v : fleet) {
            if (v.getId().equalsIgnoreCase(id)) {
                return v;
            }
        }
        return null;
    }

    private static RentalRecord findActiveRentalByVehicleId(String vehicleId) {
        for (RentalRecord r : rentals) {
            if (r.isActive() && r.getVehicle().getId().equalsIgnoreCase(vehicleId)) {
                return r;
            }
        }
        return null;
    }

    private static void displayVehicles(List<Vehicle> vehicles, String emptyMessage) {
        if (vehicles.isEmpty()) {
            System.out.println("\n" + emptyMessage);
            return;
        }

        System.out.println("\n--------------------------------------------------------------------------------");
        System.out.printf("%-6s %-13s %-17s %-10s %-14s %-15s%n",
                "ID", "Brand", "Model", "Type", "Daily Rate", "Status");
        System.out.println("--------------------------------------------------------------------------------");

        for (Vehicle v : vehicles) {
            System.out.printf("%-6s %-13s %-17s %-10s %-14s %-15s%n",
                    v.getId(), v.getBrand(), v.getModel(), v.getType(), formatMoney(v.getDailyRate()), v.getStatus());
        }
        System.out.println("--------------------------------------------------------------------------------");
    }

    private static class Payment {
        private final String paymentId;
        private String paymentMethod;
        private double totalDue;
        private double amountPaid;
        private double balanceDue;
        private double lastChange;
        private String paymentStatus;

        public Payment(String paymentId, String paymentMethod, double totalDue, double amountTendered) {
            this.paymentId = paymentId;
            this.paymentMethod = paymentMethod;
            this.totalDue = totalDue;
            this.lastChange = Math.max(0.0, amountTendered - totalDue);
            this.amountPaid = Math.min(totalDue, amountTendered);
            updateBalanceAndStatus();
        }

        public void addAdditionalCharge(double charge) {
            if (charge > 0) {
                this.totalDue += charge;
                updateBalanceAndStatus();
            }
        }

        public double recordPayment(double amountTendered, String method) {
            this.paymentMethod = method;
            double remainingBefore = this.balanceDue;
            this.lastChange = Math.max(0.0, amountTendered - remainingBefore);
            double appliedAmount = Math.min(remainingBefore, amountTendered);
            this.amountPaid += appliedAmount;
            updateBalanceAndStatus();
            return this.lastChange;
        }

        private void updateBalanceAndStatus() {
            this.balanceDue = Math.max(0.0, totalDue - amountPaid);
            if (balanceDue == 0.0) {
                this.paymentStatus = "PAID";
            } else if (amountPaid > 0.0) {
                this.paymentStatus = "PARTIAL";
            } else {
                this.paymentStatus = "PENDING";
            }
        }

        public String getPaymentId() { return paymentId; }
        public String getPaymentMethod() { return paymentMethod; }
        public double getAmountPaid() { return amountPaid; }
        public double getBalanceDue() { return balanceDue; }
        public double getLastChange() { return lastChange; }
        public String getPaymentStatus() { return paymentStatus; }
    }

    private static class Customer {
        private final String fullName;
        private final int age;
        private final String contactNumber;
        private final String driverLicenseNo;

        public Customer(String fullName, int age, String contactNumber, String driverLicenseNo) {
            this.fullName = fullName;
            this.age = age;
            this.contactNumber = contactNumber;
            this.driverLicenseNo = driverLicenseNo;
        }

        public String getFullName() { return fullName; }
        public int getAge() { return age; }
        public String getContactNumber() { return contactNumber; }
        public String getDriverLicenseNo() { return driverLicenseNo; }
    }

    private static class RentalRecord {
        private final String bookingId;
        private final Customer customer;
        private final Vehicle vehicle;
        private final LocalDate pickUpDate;
        private final LocalDate dropOffDate;
        private LocalDate actualReturnDate;
        private int lateDays;
        private double lateCharge;
        private final int days;
        private final double dailyRate;
        private final double depositAmount;
        private double totalCost;
        private final Payment payment;
        private final boolean rentedAheadOfTime;
        private boolean active;

        public RentalRecord(
                String bookingId,
                Customer customer,
                Vehicle vehicle,
                LocalDate pickUpDate,
                LocalDate dropOffDate,
                int days,
                double dailyRate,
                double depositAmount,
                Payment payment,
                boolean rentedAheadOfTime
        ) {
            this.bookingId = bookingId;
            this.customer = customer;
            this.vehicle = vehicle;
            this.pickUpDate = pickUpDate;
            this.dropOffDate = dropOffDate;
            this.actualReturnDate = null;
            this.lateDays = 0;
            this.lateCharge = 0.0;
            this.days = days;
            this.dailyRate = dailyRate;
            this.depositAmount = depositAmount;
            this.totalCost = (dailyRate * days) + depositAmount;
            this.payment = payment;
            this.rentedAheadOfTime = rentedAheadOfTime;
            this.active = true;
        }

        public void applyLateReturn(LocalDate returnDate, int lateDays, double lateCharge) {
            this.actualReturnDate = returnDate;
            this.lateDays = lateDays;
            this.lateCharge = lateCharge;
            if (lateCharge > 0) {
                this.totalCost += lateCharge;
                this.payment.addAdditionalCharge(lateCharge);
            }
        }

        public String getBookingId() { return bookingId; }
        public Customer getCustomer() { return customer; }
        public Vehicle getVehicle() { return vehicle; }
        public LocalDate getPickUpDate() { return pickUpDate; }
        public LocalDate getDropOffDate() { return dropOffDate; }
        public LocalDate getActualReturnDate() { return actualReturnDate; }
        public int getLateDays() { return lateDays; }
        public double getLateCharge() { return lateCharge; }
        public int getDays() { return days; }
        public double getDailyRate() { return dailyRate; }
        public double getTotalCost() { return totalCost; }
        public Payment getPayment() { return payment; }
        public boolean isActive() { return active; }

        public void markReturned() {
            this.active = false;
        }

        public void printReceipt() {
            double rentalSubtotal = dailyRate * days;
            String bookingStatusLabel = rentedAheadOfTime ? "Reserved (Not Available)" : "Not Available";
            System.out.println("\n==========================================");
            System.out.println("            RENTAL BOOKING RECEIPT        ");
            System.out.println("==========================================");
            System.out.println("Booking ID      : " + bookingId);
            System.out.println("Payment ID      : " + payment.getPaymentId());
            System.out.println("Booking Status  : " + bookingStatusLabel);
            System.out.println("Customer        : " + customer.getFullName());
            System.out.println("Age             : " + customer.getAge());
            System.out.println("Contact No.     : " + customer.getContactNumber());
            System.out.println("License No.     : " + customer.getDriverLicenseNo());
            System.out.println("Vehicle         : " + vehicle.getBrand() + " " + vehicle.getModel()
                    + " (" + vehicle.getId() + ", " + vehicle.getType() + ")");
            System.out.println("Pick-up Date    : " + pickUpDate);
            System.out.println("Drop-off Date   : " + dropOffDate);
            System.out.println("Daily Rate      : " + formatMoney(dailyRate));
            System.out.println("Rental Days     : " + days);
            System.out.println("Rental Subtotal : " + formatMoney(rentalSubtotal));
            System.out.println("Security Deposit: " + formatMoney(depositAmount));
            if (lateCharge > 0) {
                System.out.println("Late Days       : " + lateDays);
                System.out.println("Late Charges    : " + formatMoney(lateCharge));
            }
            System.out.println("------------------------------------------");
            System.out.println("TOTAL AMOUNT    : " + formatMoney(totalCost));
            System.out.println("Payment Method  : " + payment.getPaymentMethod());
            System.out.println("Amount Paid     : " + formatMoney(payment.getAmountPaid()));
            System.out.println("Change          : " + formatMoney(payment.getLastChange()));
            System.out.println("Balance Due     : " + formatMoney(payment.getBalanceDue()));
            System.out.println("Payment Status  : " + payment.getPaymentStatus());
            System.out.println("==========================================");
        }
    }

    private static class Vehicle {
        private final String id;
        private final String brand;
        private final String model;
        private final String type;
        private final double dailyRate;
        private String status;

        public Vehicle(String id, String brand, String model, String type, double dailyRate) {
            this.id = id;
            this.brand = brand;
            this.model = model;
            this.type = type;
            this.dailyRate = dailyRate;
            this.status = "Available";
        }

        public String getId() { return id; }
        public String getBrand() { return brand; }
        public String getModel() { return model; }
        public String getType() { return type; }
        public double getDailyRate() { return dailyRate; }
        public String getStatus() { return status; }
        public boolean isAvailable() { return "Available".equalsIgnoreCase(status); }
        public void markReserved() { this.status = "Reserved"; }
        public void markNotAvailable() { this.status = "Not Available"; }
        public void markAvailable() { this.status = "Available"; }
    }
}