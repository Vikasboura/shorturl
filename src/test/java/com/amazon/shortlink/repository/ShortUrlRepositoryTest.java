package com.amazon.shortlink.repository;

import com.amazon.shortlink.domain.ShortUrl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.model.GetItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShortUrlRepositoryTest {

    @Mock
    private DynamoDbTable<ShortUrl> shortUrlTable;

    private DynamoDbShortUrlRepository repository;

    @BeforeEach
    void setUp() {
        repository = new DynamoDbShortUrlRepository(shortUrlTable);
    }

    @Test
    @DisplayName("Should save item conditionally and return true on success")
    void testSaveWithConditionSuccess() {
        ShortUrl url = new ShortUrl("k9Z2a1x", "https://aws.amazon.com", System.currentTimeMillis(), null, false, "admin");

        boolean result = repository.saveWithCondition(url);

        assertThat(result).isTrue();
        verify(shortUrlTable, times(1)).putItem(any(PutItemEnhancedRequest.class));
    }

    @Test
    @DisplayName("Should catch ConditionalCheckFailedException and return false when collision occurs")
    void testSaveWithConditionCollision() {
        ShortUrl url = new ShortUrl("collision", "https://aws.amazon.com", System.currentTimeMillis(), null, false, "admin");

        doThrow(ConditionalCheckFailedException.builder().message("The conditional request failed").build())
                .when(shortUrlTable).putItem(any(PutItemEnhancedRequest.class));

        boolean result = repository.saveWithCondition(url);

        assertThat(result).isFalse();
        verify(shortUrlTable, times(1)).putItem(any(PutItemEnhancedRequest.class));
    }

    @Test
    @DisplayName("Should find ShortUrl by short code partition key")
    void testFindByShortCodeFound() {
        ShortUrl expected = new ShortUrl("code123", "https://amazon.com", System.currentTimeMillis(), null, false, "admin");
        when(shortUrlTable.getItem(any(Consumer.class))).thenReturn(expected);

        Optional<ShortUrl> actual = repository.findByShortCode("code123");

        assertThat(actual).isPresent();
        assertThat(actual.get().getLongUrl()).isEqualTo("https://amazon.com");
    }

    @Test
    @DisplayName("Should return empty optional when short code is not found")
    void testFindByShortCodeNotFound() {
        when(shortUrlTable.getItem(any(Consumer.class))).thenReturn(null);

        Optional<ShortUrl> actual = repository.findByShortCode("notfound");

        assertThat(actual).isEmpty();
    }
}
