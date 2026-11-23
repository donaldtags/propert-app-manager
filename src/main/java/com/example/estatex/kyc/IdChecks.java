package com.example.estatex.kyc;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Automatic checks shown to the reviewer next to the ID photos. They flag problems; the decision
 * stays with a person, except one hard rule: an ID number can't be verified on two accounts.
 */
public final class IdChecks {
    /** What a reviewer must confirm by eye before approving. */
    public static final List<String> MANUAL_CHECKS = List.of("NAME_MATCHES", "NUMBER_MATCHES", "FACE_MATCHES", "DOCUMENT_GENUINE");

    /** Zimbabwe national ID: 2-digit district, 6-7 digit serial, check letter, 2-digit district - e.g. 63-1234567 F 42. */
    private static final Pattern ZIM_NATIONAL_ID = Pattern.compile("^\\d{2}\\d{6,7}[A-Z]\\d{2}$");
    private static final Pattern PASSPORT = Pattern.compile("^[A-Z0-9]{6,9}$");
    private static final Pattern LICENCE = Pattern.compile("^[A-Z0-9]{5,15}$");

    public enum Result { PASS, WARN, FAIL }

    /** blocking: a FAIL that stops approval outright (number not in the register, person deceased). */
    public record Check(String key, String label, Result result, String detail, boolean blocking) {
        public Check(String key, String label, Result result, String detail) {
            this(key, label, result, detail, false);
        }
    }

    private IdChecks() {}

    /** "63-1234567 f 42" -> "631234567F42": how ID numbers are compared. */
    public static String normalise(String idNumber) {
        return idNumber == null ? "" : idNumber.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    public static List<Check> run(KycSubmission s, LocalDate today, List<String> otherAccountsWithSameId) {
        return run(s, today, otherAccountsWithSameId, List.of());
    }

    public static List<Check> run(KycSubmission s, LocalDate today, List<String> otherAccountsWithSameId,
                                  List<com.example.estatex.kyc.check.IdentityCheck> external) {
        List<Check> checks = new ArrayList<>();
        String id = normalise(s.getNationalIdNumber());

        Pattern format = switch (s.getIdDocumentType()) {
            case NATIONAL_ID -> ZIM_NATIONAL_ID;
            case PASSPORT -> PASSPORT;
            case DRIVERS_LICENSE -> LICENCE;
        };
        boolean formatOk = format.matcher(id).matches();
        checks.add(new Check("ID_FORMAT", "ID number is in the right format", formatOk ? Result.PASS : Result.FAIL,
                formatOk ? s.getNationalIdNumber()
                        : s.getIdDocumentType() == IdDocumentType.NATIONAL_ID
                        ? "\"" + s.getNationalIdNumber() + "\" doesn't look like a Zimbabwe ID (e.g. 63-1234567 F 42)."
                        : "\"" + s.getNationalIdNumber() + "\" doesn't look like a valid document number."));

        if (s.getDateOfBirth() == null) {
            checks.add(new Check("AGE", "Person is 18 or older", Result.FAIL, "No date of birth given."));
        } else {
            int age = Period.between(s.getDateOfBirth(), today).getYears();
            Result r = age < 18 || age > 110 ? Result.FAIL : Result.PASS;
            checks.add(new Check("AGE", "Person is 18 or older", r,
                    age < 18 ? "Under 18 (" + age + ")." : age > 110 ? "Date of birth looks wrong." : age + " years old"));
        }

        Set<String> legal = nameParts(s.getLegalFullName());
        Set<String> account = nameParts(s.getUser().getFullName());
        long shared = account.stream().filter(legal::contains).count();
        Result nameResult = account.isEmpty() || legal.isEmpty() ? Result.WARN
                : shared == account.size() ? Result.PASS : shared > 0 ? Result.WARN : Result.FAIL;
        checks.add(new Check("NAME", "Name on the account matches the legal name", nameResult,
                "Account: " + s.getUser().getFullName() + " / ID: " + s.getLegalFullName()));

        checks.add(otherAccountsWithSameId.isEmpty()
                ? new Check("DUPLICATE_ID", "ID number isn't used by another account", Result.PASS, "Not used elsewhere")
                : new Check("DUPLICATE_ID", "ID number isn't used by another account", Result.FAIL,
                "Also submitted by: " + String.join(", ", otherAccountsWithSameId)
                        + ". One person, one verified account - this may be a stolen or shared ID."));

        for (com.example.estatex.kyc.check.IdentityCheck c : external) {
            boolean registry = com.example.estatex.kyc.check.IdentityCheck.REGISTRY.equals(c.getKind());
            String label = registry ? "ID number matches the national register" : "Photo ID is genuine and the face matches";
            String st = c.getStatus();
            Result r = switch (st) {
                case "MATCH", "APPROVED" -> Result.PASS;
                case "NO_MATCH", "NOT_FOUND", "DECEASED", "DECLINED" -> Result.FAIL;
                default -> Result.WARN; // PARTIAL, PENDING, IN_REVIEW, ERROR
            };
            String detail = (c.getSummary() == null ? st : c.getSummary()) + " (" + c.getProvider() + ")";
            boolean blocking = registry && ("NOT_FOUND".equals(st) || "DECEASED".equals(st));
            checks.add(new Check(registry ? "REGISTRY" : "DOCUMENT", label, r, detail, blocking));
        }
        return checks;
    }

    private static Set<String> nameParts(String name) {
        if (name == null) return Set.of();
        String plain = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        return Arrays.stream(plain.split("[^a-z]+")).filter(p -> p.length() > 1).collect(Collectors.toSet());
    }
}
