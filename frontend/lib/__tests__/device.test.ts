import { describe, expect, it } from "vitest";
import { cleanCode, looksLikePhone } from "../device";
import { isTwoFactorChallenge } from "../types";

describe("looksLikePhone", () => {
  it("recognises phone numbers typed different ways", () => {
    for (const p of ["0771234567", "+263 77 123 4567", "263771234567", "(077) 123-4567"]) {
      expect(looksLikePhone(p)).toBe(true);
    }
  });

  it("treats emails and junk as not-a-phone", () => {
    for (const p of ["tariro@example.com", "", "12345", "call me"]) {
      expect(looksLikePhone(p)).toBe(false);
    }
  });
});

describe("cleanCode", () => {
  it("keeps the six digits of a texted or app code", () => {
    expect(cleanCode(" 123 456 ", "TOTP")).toBe("123456");
    expect(cleanCode("123-4567", "SMS")).toBe("123456");
  });

  it("keeps letters and digits of a backup code, upper-cased", () => {
    expect(cleanCode("abcde-12345", "BACKUP")).toBe("ABCDE12345");
  });
});

describe("isTwoFactorChallenge", () => {
  it("tells a challenge from a signed-in session", () => {
    expect(isTwoFactorChallenge({ twoFactorRequired: true, challengeToken: "x", methods: ["TOTP"] })).toBe(true);
    expect(isTwoFactorChallenge({ token: "t", user: { id: 1, fullName: "A", roles: [] } })).toBe(false);
  });
});
