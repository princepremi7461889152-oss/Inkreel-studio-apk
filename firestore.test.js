const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validReelPayload(uid, createdAt, updatedAt) {
  return {
    userId: uid,
    title: "Homework Excuse #42",
    socialCaption: "Wait until the last beat...",
    hashtags: ["#stickman", "#comedy", "#reels"],
    niche: "School & Teacher",
    twistStructure: "Escalating Rule-of-3 Loop",
    language: "English",
    setting: "CLASSROOM",
    bgmMood: "QUIRKY_COMEDY",
    bgmVolume: 0.25,
    captionStyle: "WORD_POP",
    captionPosition: "BOTTOM_THIRD",
    watermarkHandle: "@inkreel.studio",
    exportResolution: "HD_720P",
    clonedStyleSummary: "",
    characters: [
      {
        slot: "A",
        name: "Mr. Sharma",
        role: "Strict Teacher",
        voiceName: "Orus",
        voiceSpeed: 1.0,
        hairStyle: "BALD",
        hasGlasses: true,
        facialHair: "MUSTACHE",
        prop: "RULER",
      },
    ],
    shots: [
      {
        shotId: "shot_1",
        speaker: "A",
        framing: "WIDE_DOORWAY",
        dialogue: "Where is your assignment?",
        speechBubble: "WHERE IS IT?!",
        transitionCard: "",
        moodPose: "ANGRY",
        sfx: "DOOR_SLAM",
        durationMs: 2800,
      },
    ],
    createdAt,
    updatedAt,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write reels", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("reels").get());
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("reels")
      .doc("reel_1")
      .set(validReelPayload(ALICE_UID, now, now))
  );
});

test("Owner can create, list, get, partial-update, and delete own reel", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const updated = new Date(Date.now() - 1000);
  const reelsCol = aliceDb.collection("users").doc(ALICE_UID).collection("reels");

  await assertSucceeds(reelsCol.doc("reel_1").set(validReelPayload(ALICE_UID, created, created)));
  await assertSucceeds(reelsCol.get());
  await assertSucceeds(reelsCol.doc("reel_1").get());

  await assertSucceeds(
    reelsCol.doc("reel_1").update({
      title: "Homework Excuse Final Cut",
      bgmVolume: 0.35,
      updatedAt: updated,
    })
  );

  await assertSucceeds(reelsCol.doc("reel_1").delete());
});

test("Rejects @DocumentId 'id' in payload, ghost fields, future timestamps, and immutable createdAt overwrite", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const reelsCol = aliceDb.collection("users").doc(ALICE_UID).collection("reels");

  const withId = { ...validReelPayload(ALICE_UID, created, created), id: "reel_bad_id" };
  await assertFails(reelsCol.doc("reel_bad_id").set(withId));

  const withGhost = { ...validReelPayload(ALICE_UID, created, created), isAdmin: true };
  await assertFails(reelsCol.doc("reel_ghost").set(withGhost));

  const futureTime = new Date(Date.now() + 60000);
  await assertFails(
    reelsCol.doc("reel_future").set(validReelPayload(ALICE_UID, futureTime, futureTime))
  );

  await assertSucceeds(reelsCol.doc("reel_1").set(validReelPayload(ALICE_UID, created, created)));
  await assertFails(
    reelsCol.doc("reel_1").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Cross-user access and identity spoofing are denied", async () => {
  const created = new Date(Date.now() - 10000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx
      .firestore()
      .collection("users")
      .doc(BOB_UID)
      .collection("reels")
      .doc("bob_reel")
      .set(validReelPayload(BOB_UID, created, created));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("reels").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("reels").doc("bob_reel").get());
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("reels")
      .doc("bob_reel")
      .update({ title: "Hijacked", updatedAt: new Date(Date.now() - 500) })
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("reels")
      .doc("spoof_reel")
      .set(validReelPayload(BOB_UID, created, created))
  );
});
