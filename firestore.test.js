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
const CHARLIE_UID = "charlie_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(msOffset = 2000) {
  return new Date(Date.now() - msOffset);
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

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("squads").get());
  await assertFails(unauthDb.collection("squad_invites").doc("DUCK42").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Hacker",
      mascot: "DUCK",
      bio: "Nice try",
      dailyGoal: 8000,
      todaySteps: 0,
      todayDate: "2026-11-17",
      weeklySteps: 0,
      weekStartDate: "2026-11-16",
      totalNudgesSent: 0,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
});

test("User profile create, partial updates, and cross-user isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastDate(10000);

  // Create valid profile matching Kotlin UserProfile payload (no @DocumentId `id` field)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Quackzilla",
      mascot: "DUCK",
      avatarCode: "TUFT:GOOGLY:GRIN:TANGERINE",
      bio: "Walking for snacks only",
      dailyGoal: 8000,
      todaySteps: 1200,
      todayDate: "2026-11-17",
      weeklySteps: 5400,
      weekStartDate: "2026-11-16",
      bestDailySteps: 1200,
      activePowerUp: "",
      powerUpExpiresAtMs: 0,
      dailyHistory: { "2026-11-17": 1200 },
      totalNudgesSent: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial step sync + power-up update succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      todaySteps: 2450,
      todayDate: "2026-11-17",
      weeklySteps: 6650,
      weekStartDate: "2026-11-16",
      bestDailySteps: 2450,
      activePowerUp: "SNAILS_PACE",
      powerUpExpiresAtMs: Date.now() + 300000,
      dailyHistory: { "2026-11-17": 2450 },
      updatedAt: pastDate(500),
    })
  );

  // Partial profile + avatar customization update succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "TurboDuck",
      mascot: "FLAMINGO",
      avatarCode: "MOHAWK:PROPELLER:DERPY:LIME",
      bio: "Pink knees, fast pace",
      dailyGoal: 10000,
      updatedAt: pastDate(300),
    })
  );

  // Reject invalid power-up enum
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      activePowerUp: "HACK_INFINITE_STEPS",
      powerUpExpiresAtMs: 9999999,
      updatedAt: pastDate(250),
    })
  );

  // Reject ghost field / shadow update
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "TurboDuck",
      isAdmin: true,
      updatedAt: pastDate(200),
    })
  );

  // Reject immutable createdAt mutation
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: pastDate(100),
      updatedAt: pastDate(100),
    })
  );

  // Reject cross-user read or write
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      todaySteps: 99999,
      updatedAt: pastDate(100),
    })
  );

  // Reject future timestamp
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob",
      mascot: "POTATO",
      bio: "",
      dailyGoal: 8000,
      todaySteps: 0,
      todayDate: "2026-11-17",
      weeklySteps: 0,
      weekStartDate: "2026-11-16",
      totalNudgesSent: 0,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("Squad creation batch, invite lookup, joining, member sync, and query alignment", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const created = pastDate(10000);
  const squadId = "squad_duck_01";
  const inviteCode = "DUCK42";

  // Alice creates squad + invite + initial member doc in a WriteBatch
  const batch = aliceDb.batch();
  batch.set(aliceDb.collection("squads").doc(squadId), {
    creatorId: ALICE_UID,
    name: "Office Waddlers",
    inviteCode,
    motto: "We sprint for free donuts",
    memberIds: [ALICE_UID],
    createdAt: created,
    updatedAt: created,
  });
  batch.set(aliceDb.collection("squad_invites").doc(inviteCode), {
    squadId,
    squadName: "Office Waddlers",
    creatorId: ALICE_UID,
    createdAt: created,
  });
  batch.set(aliceDb.collection("squads").doc(squadId).collection("members").doc(ALICE_UID), {
    userId: ALICE_UID,
    squadId,
    displayName: "Quackzilla",
    mascot: "DUCK",
    bio: "Fast duck",
    dailyGoal: 8000,
    todaySteps: 3200,
    todayDate: "2026-11-17",
    weeklySteps: 12000,
    weekStartDate: "2026-11-16",
    createdAt: created,
    updatedAt: created,
  });
  await assertSucceeds(batch.commit());

  // Unfiltered squad list query fails; memberIds array-contains query succeeds
  await assertFails(aliceDb.collection("squads").get());
  await assertSucceeds(
    aliceDb.collection("squads").where("memberIds", "array-contains", ALICE_UID).get()
  );

  // Squad invites cannot be listed (prevent scraping), only looked up by exact code
  await assertFails(bobDb.collection("squad_invites").get());
  await assertSucceeds(bobDb.collection("squad_invites").doc(inviteCode).get());

  // Non-member Charlie cannot read Alice's squad or member entry
  await assertFails(charlieDb.collection("squads").doc(squadId).get());
  await assertFails(
    charlieDb.collection("squads").doc(squadId).collection("members").doc(ALICE_UID).get()
  );

  // Bob joins squad and creates his member entry
  const joinBatch = bobDb.batch();
  joinBatch.update(bobDb.collection("squads").doc(squadId), {
    memberIds: [ALICE_UID, BOB_UID],
    updatedAt: pastDate(500),
  });
  joinBatch.set(bobDb.collection("squads").doc(squadId).collection("members").doc(BOB_UID), {
    userId: BOB_UID,
    squadId,
    displayName: "CouchSpud",
    mascot: "POTATO",
    bio: "Sent from my sofa",
    dailyGoal: 6000,
    todaySteps: 850,
    todayDate: "2026-11-17",
    weeklySteps: 3100,
    weekStartDate: "2026-11-16",
    createdAt: pastDate(500),
    updatedAt: pastDate(500),
  });
  await assertSucceeds(joinBatch.commit());

  // Now Bob can read Alice's squad member entry, and Alice can read Bob's
  await assertSucceeds(
    bobDb.collection("squads").doc(squadId).collection("members").doc(ALICE_UID).get()
  );
  await assertSucceeds(
    aliceDb.collection("squads").doc(squadId).collection("members").doc(BOB_UID).get()
  );

  // List query on members subcollection is blocked (app uses single-doc gets per memberId)
  await assertFails(
    aliceDb.collection("squads").doc(squadId).collection("members").get()
  );

  // Bob cannot overwrite Alice's member entry or rename the squad
  await assertFails(
    bobDb.collection("squads").doc(squadId).collection("members").doc(ALICE_UID).update({
      todaySteps: 0,
      updatedAt: pastDate(100),
    })
  );
  await assertFails(
    bobDb.collection("squads").doc(squadId).update({
      name: "Hijacked Squad",
      updatedAt: pastDate(100),
    })
  );
});

test("Nudges: squadmates can nudge each other; outsiders cannot; recipients own/acknowledge", async () => {
  const squadId = "squad_nudge_01";
  const created = pastDate(10000);

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("squads").doc(squadId).set({
      creatorId: ALICE_UID,
      name: "Nudge Club",
      inviteCode: "NUDG99",
      motto: "Poke poke",
      memberIds: [ALICE_UID, BOB_UID],
      createdAt: created,
      updatedAt: created,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  // Alice sends a nudge to squadmate Bob
  await assertSucceeds(
    aliceDb.collection("users").doc(BOB_UID).collection("nudges").doc("nudge_1").set({
      senderId: ALICE_UID,
      senderName: "Quackzilla",
      senderMascot: "DUCK",
      recipientId: BOB_UID,
      squadId,
      message: "Snails are lapping you!",
      nudgeType: "SNAIL_PACE",
      acknowledged: false,
      createdAt: pastDate(500),
      updatedAt: pastDate(500),
    })
  );

  // Outsider Charlie cannot nudge Bob
  await assertFails(
    charlieDb.collection("users").doc(BOB_UID).collection("nudges").doc("nudge_2").set({
      senderId: CHARLIE_UID,
      senderName: "Stranger",
      senderMascot: "CRAB",
      recipientId: BOB_UID,
      squadId,
      message: "Spam!",
      nudgeType: "QUACK_ATTACK",
      acknowledged: false,
      createdAt: pastDate(500),
      updatedAt: pastDate(500),
    })
  );

  // Bob can list and acknowledge his nudges; Alice cannot read Bob's nudges
  await assertSucceeds(bobDb.collection("users").doc(BOB_UID).collection("nudges").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("nudges").get());
  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).collection("nudges").doc("nudge_1").update({
      acknowledged: true,
      updatedAt: pastDate(100),
    })
  );
});
