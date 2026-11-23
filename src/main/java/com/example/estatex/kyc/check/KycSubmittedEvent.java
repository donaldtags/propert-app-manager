package com.example.estatex.kyc.check;

/** Published when someone sends their ID for checking; triggers the register lookup after commit. */
public record KycSubmittedEvent(Long submissionId) {}
