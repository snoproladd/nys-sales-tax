package com.jakeladd.salestax;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;
import java.util.Locale;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Hello world!
 *
 */
public class App {
    public static void main(String[] args) {
        Scanner inputScanner = new Scanner(System.in);
        try {
            greeting(inputScanner);
        } catch (IOException | SQLException e) {
            System.out.println("Database error: " + e.getMessage());
            return; // ends main: no point continuing if the database is unreachable
        }
        String jurisdiction = chooseJurisdiction(inputScanner);
        if (jurisdiction != null) {
            try {
                Jurisdiction picked = lookupJurisdiction(inputScanner, jurisdiction);
                if (picked != null) {
                    BigDecimal amount = askAmount(inputScanner);
                    LocalDate transDate = askDate(inputScanner);
                    Optional<TaxResult> result = TaxCalculator.calculate(picked, amount, transDate);
                    if (result.isPresent()) {
                        printResult(picked, amount, transDate, result.get());
                    } else {
                        System.out.println("No rate on file for " + picked.name() + " on " + transDate + ".");
                    }
                } else {

                }

            } catch (IOException | SQLException e) {
                System.out.println("Database error: " + e.getMessage());

            }
        }

        inputScanner.close();
    }

    private static void greeting(Scanner scanner) throws SQLException, IOException {
        System.out.println("==============================================\n" + //
                "  NYS Sales Tax Lookup\n" +
                "  Rates from " + DataSourceDao.latestEdition("718") + "\n" +
                "  see also " + DataSourceDao.latestEdition("718-A") + "\n" +
                "==============================================\n");
        System.out.println("Press ENTER to continue...");
        scanner.nextLine();
    }

    private static String chooseJurisdiction(Scanner scanner) {
        System.out.println("Enter a county, city, town or village (or Q to quit)");
        String jurisdictionRaw = scanner.nextLine().trim();
        while (jurisdictionRaw.isEmpty()) {
            System.out.println("Please enter a name.");
            jurisdictionRaw = scanner.nextLine().trim();
        }
        if (jurisdictionRaw.equalsIgnoreCase("q")) {
            System.out.println("You have terminated the program.");
            return null;
        } else {
            String jurisdiction = jurisdictionRaw.substring(0, 1).toUpperCase(Locale.ROOT)
                    + jurisdictionRaw.substring(1);
            return jurisdiction;
        }

    }

    private static Jurisdiction lookupJurisdiction(Scanner scanner, String jurisdiction)
            throws IOException, SQLException {

        List<Jurisdiction> j = JurisdictionDao.findByName(jurisdiction);
        while (j.size() == 0) {
            System.out.println("No jurisdiction found for " + jurisdiction + ". Try again");
            jurisdiction = chooseJurisdiction(scanner);
            if (jurisdiction == null) {
                return null;
            }
            j = JurisdictionDao.findByName(jurisdiction);
        }
        ;
        if (j.size() == 1) {
            return j.get(0);

        } else {
            System.out.printf("%-4s %-20s %-16s %-16s%n", "#", "Name", "Type", "County");
            System.out.printf("%-4s %-20s %-16s %-16s%n", "--", "----", "----", "------");
            for (int i = 0; i < j.size(); i++) {
                Jurisdiction current = j.get(i);
                System.out.printf("%-4s %-20s %-16s %-16s%n", (i + 1), current.name(), current.type(),
                        current.countyName() == null ? "" : current.countyName());
            }
            while (true) {
                System.out.println("Choose a number between 1 and " + j.size() + ": ");
                String input = scanner.nextLine().trim();
                try {
                    int numChosen = Integer.parseInt(input);
                    if (numChosen >= 1 && numChosen <= j.size()) {
                        return j.get(numChosen - 1);
                    }
                    System.out.println("Please choose a number between 1 and " + j.size() + ".");
                } catch (NumberFormatException e) {
                    System.out.println("'" + input + "' is not a number.");
                }

            }
        }
    }

    private static BigDecimal askAmount(Scanner scanner) {
        while (true) {
            System.out.print(
                    "Transaction amount (include a decimal point for cents, maximum 2 decimal places): $");
            String text = scanner.nextLine().trim();
            try {
                BigDecimal amount = new BigDecimal(text);
                if (amount.scale() > 2) {
                    System.out.println("Amounts can have at most 2 decimal places");
                    continue;
                }
                if (amount.compareTo(BigDecimal.ZERO) > 0) {
                    return amount;
                }

                System.out.println("Please enter a number greater than 0.");
            } catch (NumberFormatException e) {
                System.out.println("'" + text + "' is not a number.");
            }
        }

    }

    private static LocalDate askDate(Scanner scanner) {
        while (true) {
            System.out.println("Transaction Date (must be entered as YYYY-MM-DD) Tip: Press ENTER for today");
            String text = scanner.nextLine().trim();
            LocalDate today = LocalDate.now();
            if (text.isEmpty()) {
                System.out.println("You have entered today's date: " + today);
                return today;
            }
            try {
                LocalDate date = LocalDate.parse(text);
                System.out.println("You entered: " + date);
                if (date.isAfter(today)) {
                    System.out.print("That date is in the future. Use it anyway? (Y/N): ");
                    String confirm = scanner.nextLine().trim();
                    if (confirm.equalsIgnoreCase("y") || confirm.equalsIgnoreCase("yes")) {
                        return date;
                    } else {
                        System.out.println("Ok, enter a different date.");
                        continue;
                    }
                }
                return date;
            } catch (DateTimeParseException e) {
                System.out.println(text + " is not a valid date!\n Please enter date as YYYY-MM-DD");
            }
        }
    }

    private static void printResult(Jurisdiction j, BigDecimal amount, LocalDate date, TaxResult r) {
        String line = "----------------------------------------------";
        String rate = r.combinedRate().stripTrailingZeros().toPlainString() + "%";
        String type = j.type().replace("_", " ");

        System.out.println();
        System.out.println(line);
        System.out.printf("  %-17s %s (%s)%n", "Jurisdiction:", j.name(), type);
        System.out.printf("  %-17s %s%n", "Date:", date);
        System.out.printf("  %-17s %s%n", "Combined rate:", rate);
        System.out.printf("  %-17s %s%n", "Rate effective:", r.effectiveDate());
        System.out.printf("  %-17s $%s%n", "Purchase:", amount.setScale(2));
        System.out.printf("  %-17s $%s%n", "Tax:", r.tax());
        System.out.printf("  %-17s $%s%n", "Total:", r.total().setScale(2));
        System.out.println();
        System.out.println("  Note: " + r.note());
        if (!r.footnotes().isEmpty()) {
            System.out.println("  Footnotes:");
            for (String f : r.footnotes()) {
                System.out.println("    " + f);
            }
        }
        System.out.println(line);
        System.out.println();
    }

}
