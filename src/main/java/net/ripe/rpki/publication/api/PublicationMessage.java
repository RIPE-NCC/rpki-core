package net.ripe.rpki.publication.api;

import com.google.common.base.Preconditions;
import com.google.common.io.BaseEncoding;
import net.ripe.rpki.commons.util.EqualsSupport;

import java.net.URI;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Predicate;

public interface PublicationMessage {

    class PublishRequest extends EqualsSupport implements PublicationMessage {
        private final URI uri;

        private final byte[] content;
        public final Optional<String> hashToReplace;

        public PublishRequest(URI uri, byte[] content, Optional<String> hashToReplace) {
            this.uri = Preconditions.checkNotNull(uri, "uri is required");
            this.content = Arrays.copyOf(content, content.length);
            this.hashToReplace = hashToReplace;
        }

        public URI getUri() {
            return uri;
        }

        public byte[] getContent() {
            return Arrays.copyOf(content, content.length);
        }

        public String getBase64Content() {
            return BaseEncoding.base64().encode(content);
        }

        @Override
        public String toString() {
            return String.format("PublishRequest [uri=%s, hash=%s]",
                    uri, hashToReplace.orElse("<Absent>"));
        }

        public String toStringFull() {
            return String.format("PublishRequest [uri=%s, content=%s, hash=%s]",
                    uri, getBase64Content(), hashToReplace.orElse("<Absent>"));
        }
    }

    class PublishReply extends EqualsSupport implements PublicationMessage {
        private final URI uri;

        public PublishReply(URI uri) {
            this.uri = Preconditions.checkNotNull(uri, "uri is required");
        }

        public URI getUri() {
            return uri;
        }

        @Override
        public String toString() {
            return "PublishReply [uri=" + uri + "]";
        }
    }

    class WithdrawRequest extends EqualsSupport implements PublicationMessage {
        private final URI uri;
        public final String hash;

        public WithdrawRequest(URI uri, String hash) {
            this.uri = Preconditions.checkNotNull(uri, "uri is required");
            this.hash = Preconditions.checkNotNull(hash, "hash is required");
        }

        public URI getUri() {
            return uri;
        }

        @Override
        public String toString() {
            return String.format("WithdrawRequest [uri=%s; hash=%s]", uri, hash);
        }
    }

    class WithdrawReply extends EqualsSupport implements PublicationMessage {
        private final URI uri;

        public WithdrawReply(URI uri) {
            this.uri = Preconditions.checkNotNull(uri, "uri is required");
        }

        public URI getUri() {
            return uri;
        }

        @Override
        public String toString() {
            return "WithdrawReply [uri=" + uri + "]";
        }
    }

    class ErrorReply extends EqualsSupport implements PublicationMessage {
        private final String errorCode;

        private final String message;

        public ErrorReply(String errorCode, String message) {
            this.errorCode = Preconditions.checkNotNull(errorCode, "error code is required");
            this.message = message;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return "ErrorReply [errorCode=" + errorCode + ", message=" + message + "]";
        }
    }

    class ListRequest extends EqualsSupport implements PublicationMessage {}

    class ListReply extends EqualsSupport implements PublicationMessage {
        public final URI uri;
        public final String hash;

        public ListReply(URI uri, String hash) {
            this.uri = uri;
            this.hash = hash;
        }
    }

    Predicate<PublicationMessage> isListReply = input -> (input instanceof ListReply);
    Predicate<PublicationMessage> isErrorReply = input -> (input instanceof ErrorReply);
}
