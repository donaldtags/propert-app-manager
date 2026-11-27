import { describe, expect, it } from "vitest";
import { cleanReferralCode } from "../referralCode";

describe("referral codes", () => {
  it("are normalised the same way the server does", () => {
    expect(cleanReferralCode(" ab3k-9xz2 ")).toBe("AB3K9XZ2");
    expect(cleanReferralCode("a".repeat(30))).toHaveLength(12);
  });
});
