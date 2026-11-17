package com.example.primenestprop.organisation;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Brand tokens an organisation workspace renders with. The frontend maps these onto its semantic
 * CSS variables, so a new agency gets its own look without a code change. Fonts are names from a
 * fixed set the frontend bundles (see frontend/lib/orgTheme.ts); anything else falls back.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class OrgTheme {
    @Column(length = 9) private String lightPrimary;
    @Column(length = 9) private String lightSurface;
    @Column(length = 9) private String lightText;
    @Column(length = 9) private String lightMuted;
    @Column(length = 9) private String lightAccent;
    @Column(length = 9) private String darkPrimary;
    @Column(length = 9) private String darkSurface;
    @Column(length = 9) private String darkText;
    @Column(length = 9) private String darkMuted;
    @Column(length = 9) private String darkAccent;
    @Column(length = 40) private String headingFont;
    @Column(length = 40) private String bodyFont;
}
