package net.javacrumbs.shedlock.provider.arangodb;

import com.arangodb.ArangoCollection;
import com.arangodb.ArangoDB;
import com.arangodb.ArangoDatabase;
import com.arangodb.entity.BaseDocument;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import java.time.Instant;

import static net.javacrumbs.shedlock.provider.arangodb.ArangoLockProvider.COLLECTION_NAME;
import static net.javacrumbs.shedlock.provider.arangodb.ArangoLockProvider.LOCKED_AT;
import static net.javacrumbs.shedlock.provider.arangodb.ArangoLockProvider.LOCKED_BY;
import static net.javacrumbs.shedlock.provider.arangodb.ArangoLockProvider.LOCK_UNTIL;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class ArangoLockProviderIntegrationTest extends AbstractLockProviderIntegrationTest {

    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "";
    private static final String DB_HOSTNAME = "localhost";
    private static final int DB_PORT = 8529;

    private static final String DB_NAME = "arangodb";

    private static ArangoDB arango;
    private static ArangoDatabase arangoDatabase;
    private static ArangoCollection arangoCollection;

    @BeforeAll
    static void beforeAll() {

        arango = new ArangoDB.Builder()
            .host(DB_HOSTNAME, DB_PORT)
            .user(DB_USER)
            .password(DB_PASSWORD)
            .useSsl(false)
            .build();

        if (!arango.getDatabases().contains(DB_NAME)) {
            arango.createDatabase(DB_NAME);
        }

        arangoDatabase = arango.db(DB_NAME);

        if (arangoDatabase.getCollections().stream()
            .anyMatch(collectionEntity -> collectionEntity.getName().equals(COLLECTION_NAME))) {
            arangoCollection = arangoDatabase.collection(COLLECTION_NAME);
            arangoCollection.drop();
        }
    }

    @AfterAll
    static void afterAll() {
        arango.db(DB_NAME).drop();
        arango.shutdown();
    }

    @BeforeEach
    void setUp() {
        arangoDatabase.createCollection(COLLECTION_NAME);
        arangoCollection = arangoDatabase.collection(COLLECTION_NAME);
    }

    @AfterEach
    void tearDown() {
        arangoCollection.drop();
    }

    @Override
    protected LockProvider getLockProvider() {
        return new ArangoLockProvider(arangoDatabase);
    }

    @Override
    protected void assertUnlocked(String lockName) {
        BaseDocument document = getDocument(lockName);
        Instant instantLockedAt = Instant.parse((String) document.getAttribute(LOCKED_AT));
        Instant instantLockUntil = Instant.parse((String) document.getAttribute(LOCK_UNTIL));

        assertFalse(((String) document.getAttribute(LOCKED_BY)).isEmpty());
        assertTrue(isBeforeOrEquals(instantLockedAt, ClockProvider.now()));
        assertTrue(isBeforeOrEquals(instantLockUntil, ClockProvider.now()));
    }

    @Override
    protected void assertLocked(String lockName) {
        BaseDocument document = getDocument(lockName);

        Instant instantLockedAt = Instant.parse(document.getAttribute(LOCKED_AT).toString());
        Instant instantLockUntil = Instant.parse(document.getAttribute(LOCK_UNTIL).toString());

        assertFalse(document.getAttribute(LOCKED_BY).toString().isEmpty());
        assertTrue(isBeforeOrEquals(instantLockedAt, ClockProvider.now()));
        assertTrue(instantLockUntil.isAfter(ClockProvider.now()));
    }

    private BaseDocument getDocument(String lockName) {
        return arangoCollection.getDocument(lockName, BaseDocument.class);
    }

    private boolean isBeforeOrEquals(Instant instantOne, Instant instantTwo) {
        return instantOne.compareTo(instantTwo) <= 0;
    }

}