package com.mailflow.repository;

import com.mailflow.model.Email;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.Instant;
import java.util.List;

public interface EmailRepository extends MongoRepository<Email, String> {

    // Inbox: I'm the receiver, not draft, sent, not trashed/deleted by me
    @Query("{ 'receiver': ?0, 'isDraft': false, 'isSent': true, 'trashedByReceiver': false, 'deletedByReceiver': false }")
    Page<Email> findInbox(String userId, Pageable pageable);

    @Query("{ 'sender': ?0, 'isDraft': false, 'isSent': true, 'trashedBySender': false, 'deletedBySender': false }")
    Page<Email> findSent(String userId, Pageable pageable);

    @Query("{ 'sender': ?0, 'isDraft': true }")
    Page<Email> findDrafts(String userId, Pageable pageable);

    @Query("{ '$and': [ " +
           "  { '$or': [ { 'sender': ?0, 'trashedBySender': false, 'deletedBySender': false }, " +
           "             { 'receiver': ?0, 'trashedByReceiver': false, 'deletedByReceiver': false } ] }, " +
           "  { 'isStarred': true }, { 'isDraft': false } ] }")
    Page<Email> findStarred(String userId, Pageable pageable);

    @Query("{ '$and': [ " +
           "  { '$or': [ { 'sender': ?0, 'trashedBySender': false, 'deletedBySender': false }, " +
           "             { 'receiver': ?0, 'trashedByReceiver': false, 'deletedByReceiver': false } ] }, " +
           "  { 'isImportant': true }, { 'isDraft': false } ] }")
    Page<Email> findImportant(String userId, Pageable pageable);

    @Query("{ '$and': [ " +
           "  { '$or': [ { 'sender': ?0, 'trashedBySender': false, 'deletedBySender': false }, " +
           "             { 'receiver': ?0, 'trashedByReceiver': false, 'deletedByReceiver': false } ] }, " +
           "  { 'isArchived': true }, { 'isDraft': false } ] }")
    Page<Email> findArchived(String userId, Pageable pageable);

    @Query("{ 'sender': ?0, 'isDraft': false, 'isSent': false, 'scheduledTime': { '$ne': null } }")
    Page<Email> findScheduled(String userId, Pageable pageable);

    @Query("{ '$or': [ " +
           "  { 'sender': ?0, 'trashedBySender': true, 'deletedBySender': false }, " +
           "  { 'receiver': ?0, 'trashedByReceiver': true, 'deletedByReceiver': false } ] }")
    Page<Email> findTrash(String userId, Pageable pageable);

    @Query("{ '$and': [ " +
           "  { '$or': [ { 'sender': ?0 }, { 'receiver': ?0 } ] }, " +
           "  { '$or': [ { 'subject': { '$regex': ?1, '$options': 'i' } }, { 'body': { '$regex': ?1, '$options': 'i' } } ] } ] }")
    Page<Email> search(String userId, String regex, Pageable pageable);

    // For the scheduled-email delivery cron
    List<Email> findByIsDraftFalseAndIsSentFalseAndScheduledTimeLessThanEqualAndReceiverIsNotNull(Instant now);

    // For the trash-purge cron
    @Query("{ 'trashedAt': { '$lte': ?0 }, '$or': [ { 'trashedBySender': true }, { 'trashedByReceiver': true } ] }")
    List<Email> findExpiredTrash(Instant cutoff);

    Page<Email> findByIsDraftFalse(Pageable pageable);

    long countByIsDraftFalseAndIsSentTrue();
    long countByIsSentTrueAndCreatedAtGreaterThanEqual(Instant startOfDay);

    @Query(value = "{ '$or': [ { 'trashedBySender': true }, { 'trashedByReceiver': true } ] }", count = true)
    long countTrashed();

    @Query(value = "{ '$or': [ { 'deletedBySender': true }, { 'deletedByReceiver': true } ] }", count = true)
    long countDeleted();

    List<Email> findTop10ByIsDraftFalseAndIsSentTrueOrderByCreatedAtDesc();

    void deleteAllBySenderAndFolder(String sender, String folder);
}
