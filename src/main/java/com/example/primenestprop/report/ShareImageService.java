package com.example.primenestprop.report;

import com.example.primenestprop.common.ApiException;
import com.example.primenestprop.listing.ListingCompliance;
import com.example.primenestprop.listing.ListingComplianceRepository;
import com.example.primenestprop.organisation.OrgAccessService;
import com.example.primenestprop.organisation.OrgAccessService.OrgContext;
import com.example.primenestprop.organisation.OrgPermission;
import com.example.primenestprop.organisation.OrgTheme;
import com.example.primenestprop.organisation.Organisation;
import com.example.primenestprop.property.ListingType;
import com.example.primenestprop.property.Property;
import com.example.primenestprop.property.PropertyRepository;
import com.example.primenestprop.property.PropertyService;
import com.example.primenestprop.property.TitleType;
import com.example.primenestprop.user.AppUser;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 1080x1080 Instagram-ready listing image: photo, price, beds, baths, suburb and the
 * organisation's name in its colours. Rendered on the server so it works for every photo source.
 * The property photo is only used when the owner has consented to photos being shown.
 */
@Service
public class ShareImageService {
    static final int SIZE = 1080;

    static {
        System.setProperty("java.awt.headless", "true");
    }

    private final OrgAccessService access;
    private final PropertyRepository properties;
    private final PropertyService propertyService;
    private final ListingComplianceRepository compliances;

    public ShareImageService(OrgAccessService access, PropertyRepository properties, PropertyService propertyService,
                             ListingComplianceRepository compliances) {
        this.access = access;
        this.properties = properties;
        this.propertyService = propertyService;
        this.compliances = compliances;
    }

    @Transactional(readOnly = true)
    public byte[] render(String slug, Long propertyId, AppUser user) {
        OrgContext ctx = access.require(user, slug, OrgPermission.ORG_VIEW);
        Property p = properties.findById(propertyId)
                .filter(x -> ctx.organisation().getId().equals(x.getOrganisationId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Listing not found"));
        boolean photoConsent = compliances.findByPropertyId(p.getId()).map(ListingCompliance::isOwnerConsentPhotos).orElse(false);
        BufferedImage photo = null;
        if (photoConsent) {
            photo = propertyService.readStoredPhoto(p.getId()).map(ShareImageService::decode).orElse(null);
        }
        return png(draw(ctx.organisation(), p, photo));
    }

    static BufferedImage draw(Organisation org, Property p, BufferedImage photo) {
        OrgTheme t = org.getTheme();
        Color navy = color(t == null ? null : t.getLightPrimary(), "#0B1D32");
        Color ivory = color(t == null ? null : t.getLightSurface(), "#F5F2EA");
        Color silver = color(t == null ? null : t.getLightAccent(), "#C9CED4");

        BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        int photoHeight = 720;
        if (photo != null) {
            // cover-fit the photo into the top area
            double scale = Math.max((double) SIZE / photo.getWidth(), (double) photoHeight / photo.getHeight());
            int w = (int) Math.round(photo.getWidth() * scale);
            int h = (int) Math.round(photo.getHeight() * scale);
            g.drawImage(photo, (SIZE - w) / 2, (photoHeight - h) / 2, w, h, null);
        } else {
            g.setPaint(new GradientPaint(0, 0, navy, SIZE, photoHeight, navy.brighter()));
            g.fillRect(0, 0, SIZE, photoHeight);
            g.setColor(silver);
            g.setStroke(new BasicStroke(10f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int cx = SIZE / 2;
            g.drawPolyline(new int[]{cx - 180, cx, cx + 180}, new int[]{360, 210, 360}, 3); // roof line
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 46));
            centered(g, org.getName().toUpperCase(Locale.ROOT), 470);
        }

        // listing type tag
        g.setColor(navy);
        g.fillRoundRect(40, 40, 230, 64, 16, 16);
        g.setColor(ivory);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
        g.drawString(p.getListingType() == ListingType.SALE ? "FOR SALE" : "TO LET", 66, 84);
        if (p.getTitleType() == TitleType.CESSION) {
            g.setColor(new Color(0xFEF3C7));
            g.fillRoundRect(SIZE - 250, 40, 210, 64, 16, 16);
            g.setColor(new Color(0x92400E));
            g.drawString("CESSION", SIZE - 222, 84);
        }

        // info panel
        g.setColor(ivory);
        g.fillRect(0, photoHeight, SIZE, SIZE - photoHeight);
        g.setColor(navy);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 76));
        String price = money(p);
        g.drawString(price, 56, photoHeight + 110);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 38));
        g.drawString(p.getBedrooms() + " bed  ·  " + p.getBathrooms() + " bath  ·  " + p.getSuburb(), 60, photoHeight + 180);
        g.setColor(color(t == null ? null : t.getLightMuted(), "#5E6873"));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 30));
        g.drawString(p.getCity(), 60, photoHeight + 226);

        // brand footer
        g.setColor(navy);
        g.fillRect(0, SIZE - 82, SIZE, 82);
        g.setColor(silver);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 32));
        g.drawString(spaced(org.getName().toUpperCase(Locale.ROOT)), 56, SIZE - 30);
        if (org.getWhatsappNumber() != null) {
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 28));
            String contact = "+" + org.getWhatsappNumber();
            FontMetrics fm = g.getFontMetrics();
            g.drawString(contact, SIZE - 56 - fm.stringWidth(contact), SIZE - 30);
        }
        g.dispose();
        return img;
    }

    private static BufferedImage decode(byte[] bytes) {
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            return null;
        }
    }

    static byte[] png(BufferedImage img) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not create the image");
        }
    }

    private static void centered(Graphics2D g, String text, int y) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (SIZE - fm.stringWidth(text)) / 2, y);
    }

    private static String spaced(String s) {
        return String.join(" ", s.split(""));
    }

    private static String money(Property p) {
        if (p.getPrice() == null) {
            return "Price on request";
        }
        String symbol = "USD".equals(p.getCurrency()) ? "US$" : p.getCurrency() + " ";
        String amount = symbol + String.format("%,.0f", p.getPrice().doubleValue());
        return p.getListingType() == ListingType.SALE ? amount : amount + "/mo";
    }

    private static Color color(String hex, String fallback) {
        String v = hex != null && hex.matches("^#[0-9A-Fa-f]{6}$") ? hex : fallback;
        return Color.decode(v);
    }
}
