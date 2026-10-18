/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 05/14/2026 mir0n  created: Chain POST /esq-cmd-new kind=34 client user under session officeId; fat-fill USR/person/addr/bizaddr fields; saves userId + userEmail
 * 08/26/2026 mir0n  userEmail builds the domain inline
 * 10/13/2026 mir0n  v2.0.0 -- moved into services2/hauberk, the Esquire half of the harness: the
 *                   generic machinery is Armoury's armoury-hauberk, this project is the Esquire
 *                   routines over it
 */
package pro.mir0n.esquire.hauberk.chain;

import io.gatling.javaapi.core.ChainBuilder;
import pro.mir0n.esquire.hauberk.config.EntityKinds;

import java.util.concurrent.ThreadLocalRandom;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * POST /esq-cmd-new for kind=34 (Client user) under the office captured by
 * CreateOffice / EnsureOffice. Generates a fresh random local-part each call
 * so multiple runs (and parallel VUs) never collide on the unique-email
 * constraint.
 *
 * Body shape: enyMan's UsrService.createUsr() reads name and email from a
 * nested "person" sub-entity (kind=992), then derives loginId = email.
 * Top-level loginId / email / name are ignored.
 *
 * Fat-fill (every applicable field set, non-null):
 *   - USR top-level: only desc + registration are client-settable. Rest
 *     (loginId, name, email, deleted) is server-derived from the primary
 *     person sub-entity or server-managed.
 *   - person  (kind=992): firstName, middleName, lastName, title, dob,
 *                         birthPlace, sex, taxId, citizenship, marStatus,
 *                         personIdType, personIdNumber, email, phone, phone2
 *   - addr    (kind=988, postal): addr, addr2, city, country, postalCode,
 *                                 province  (no company/dept/fax/title/url
 *                                 on postal -- those are biz-only)
 *   - bizaddr (kind=990, business): the full set incl. company, department,
 *                                   fax, title, url
 *
 * Values are deterministic strings so future read/update tests can assert
 * against known content. Per-call uniqueness comes from {userSlug} +
 * {userEmail}.
 *
 * Session inputs:
 *   officeId   -- parent office id (from CreateOffice / EnsureOffice / CreateSubOffice)
 * Session outputs:
 *   userSlug   -- random local-part, e.g. "hauberk-7c3d8f912a"
 *   userEmail  -- "${userSlug}@mir0n.pro" (also the loginId on the server)
 *   userId     -- new user id (server-generated)
 */
public final class CreateUser {

    private CreateUser() {}

    public static final ChainBuilder chain =
        exec(session -> {
            String slug = "hauberk-" + Long.toHexString(
                    ThreadLocalRandom.current().nextLong() & 0xFFFFFFFFFFL);
            return session
                    .set("userSlug",  slug)
                    .set("userEmail", slug + "@mir0n.pro");
        })
        .exec(http("POST /esq-cmd-new (user)")
            .post("/esq-cmd-new")
            .queryParam("kind",     EntityKinds.USR_CLIENT)
            .queryParam("parentId", "#{officeId}")
            .queryParam("cmd",      "new")
            .header("Content-Type", "application/json")
            .body(StringBody(
                "{"
              +   "\"desc\":\"hauberk test user\","
              +   "\"registration\":\"S\","
              +   "\"person\":{"
              +     "\"firstName\":\"#{userSlug}\","
              +     "\"middleName\":\"M\","
              +     "\"lastName\":\"hauberk\","
              +     "\"title\":\"Mr.\","
              +     "\"dob\":\"1990-01-15\","
              +     "\"birthPlace\":\"Toronto\","
              +     "\"sex\":\"M\","
              +     "\"taxId\":\"123-45-6789\","
              +     "\"citizenship\":\"CA\","
              +     "\"marStatus\":\"S\","
              +     "\"personIdType\":\"P\","
              +     "\"personIdNumber\":\"P1234567\","
              +     "\"email\":\"#{userEmail}\","
              +     "\"phone\":\"+14165551234\","
              +     "\"phone2\":\"+14165551235\""
              +   "},"
              +   "\"addr\":{"
              +     "\"addr\":\"123 Main St\","
              +     "\"addr2\":\"Apt 4\","
              +     "\"city\":\"Toronto\","
              +     "\"country\":\"CA\","
              +     "\"postalCode\":\"M5V 3A8\","
              +     "\"province\":\"ON\""
              +   "},"
              +   "\"bizaddr\":{"
              +     "\"addr\":\"500 King St W\","
              +     "\"addr2\":\"Suite 100\","
              +     "\"city\":\"Toronto\","
              +     "\"company\":\"mir0n & co\","
              +     "\"country\":\"CA\","
              +     "\"department\":\"Engineering\","
              +     "\"fax\":\"+14165552000\","
              +     "\"postalCode\":\"M5V 1L7\","
              +     "\"province\":\"ON\","
              +     "\"title\":\"hauberk test office\","
              +     "\"url\":\"https://mir0n.pro\""
              +   "}"
              + "}"))
            .check(status().is(200))
            .check(jsonPath("$.id").saveAs("userId")));
}
