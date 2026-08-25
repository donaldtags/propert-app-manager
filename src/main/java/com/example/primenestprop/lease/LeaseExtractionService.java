package com.example.primenestprop.lease;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.Base64PdfSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.DocumentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;
import com.example.primenestprop.ai.AiAnthropicConfig;
import com.example.primenestprop.common.ApiException;
import com.example.primenestprop.common.DocxTextExtractor;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Reads an uploaded lease document (PDF, PNG/JPEG scan, or Word doc) and asks Claude to extract
 * the key lease terms, so agents/landlords can pre-fill the create-lease form instead of retyping
 * a document they already have.
 */
@Service
public class LeaseExtractionService {
    private static final Logger log = LoggerFactory.getLogger(LeaseExtractionService.class);
    private static final long MAX_FILE_SIZE = 8L * 1024L * 1024L;
    private static final Set<String> DOCX_TYPES = Set.of(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final AnthropicClient client;
    private final Model model;

    public LeaseExtractionService(AiAnthropicConfig anthropicConfig) {
        this.client = anthropicConfig.client();
        this.model = anthropicConfig.model();
    }

    public LeaseExtractionResult extract(MultipartFile file) {
        if (client == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Auto-fill from document requires the AI assistant to be configured (ANTHROPIC_API_KEY)");
        }
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A document file is required");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "File size must be 8MB or less");
        }

        ContentBlockParam documentBlock = toContentBlock(file);
        ContentBlockParam instructionBlock = ContentBlockParam.ofText(
                TextBlockParam.builder()
                        .text("Extract the lease terms from the attached document into the structured fields. "
                                + "Only set a field when the document genuinely states it; leave every other field "
                                + "null rather than guessing.")
                        .build());

        StructuredMessageCreateParams<LeaseExtractionResult> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(1024L)
                .system("You extract structured lease/rental agreement data from documents for the PrimeNest "
                        + "property platform. Never invent values that are not present in the document.")
                .outputConfig(LeaseExtractionResult.class)
                .addUserMessageOfBlockParams(List.of(documentBlock, instructionBlock))
                .build();

        try {
            return client.messages().create(params).content().stream()
                    .flatMap(block -> block.text().stream())
                    .findFirst()
                    .map(structured -> structured.text())
                    .orElseThrow(() -> new IllegalStateException("Claude returned no structured lease data"));
        } catch (Exception ex) {
            log.warn("Lease document extraction failed", ex);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Could not read that document. Try a clearer scan or PDF.");
        }
    }

    private ContentBlockParam toContentBlock(MultipartFile file) {
        String contentType = file.getContentType() == null ? "" : file.getContentType();
        try {
            if ("application/pdf".equals(contentType)) {
                String base64 = Base64.getEncoder().encodeToString(file.getBytes());
                Base64PdfSource source = Base64PdfSource.builder().data(base64).build();
                return ContentBlockParam.ofDocument(DocumentBlockParam.builder().source(source).build());
            }
            if (contentType.startsWith("image/")) {
                Base64ImageSource.MediaType mediaType = switch (contentType) {
                    case "image/png" -> Base64ImageSource.MediaType.IMAGE_PNG;
                    case "image/jpeg" -> Base64ImageSource.MediaType.IMAGE_JPEG;
                    case "image/webp" -> Base64ImageSource.MediaType.IMAGE_WEBP;
                    case "image/gif" -> Base64ImageSource.MediaType.IMAGE_GIF;
                    default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported image type: " + contentType);
                };
                String base64 = Base64.getEncoder().encodeToString(file.getBytes());
                Base64ImageSource source = Base64ImageSource.builder().mediaType(mediaType).data(base64).build();
                return ContentBlockParam.ofImage(ImageBlockParam.builder().source(source).build());
            }
            if (DOCX_TYPES.contains(contentType)) {
                String text = DocxTextExtractor.extract(file.getInputStream());
                return ContentBlockParam.ofDocument(DocumentBlockParam.builder().textSource(text).build());
            }
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Unsupported file type. Upload a PDF, PNG, JPEG, or Word (.docx) document.");
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Could not read the uploaded file");
        }
    }
}
