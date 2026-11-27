const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_farmer_123";
const BOB_UID = "bob_farmer_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated users are denied reads and writes across all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("activities").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .set({
        userId: ALICE_UID,
        ownerName: "Alice",
        farmName: "GreenHarvest Farms",
        currencySymbol: "₦",
        onboardingCompleted: false,
        onboardingStep: 0,
        createdAt: now,
        updatedAt: now,
      })
  );
});

test("FarmProfile: owner can create, read, and partially update; cross-user and ghost fields fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      ownerName: "Ada",
      farmName: "GreenHarvest Farms",
      currencySymbol: "₦",
      onboardingCompleted: false,
      onboardingStep: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial update preserves createdAt
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .update({
        onboardingCompleted: true,
        onboardingStep: 7,
        dailyGoalNote: "Reach 500 bags of maize this season",
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // Ghost field rejected
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .update({
        isAdmin: true,
        updatedAt: new Date(Date.now() - 500),
      })
  );

  // Immutable createdAt mutation rejected
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .update({
        createdAt: new Date(Date.now() - 500),
        updatedAt: new Date(Date.now() - 500),
      })
  );

  // Future timestamp rejected
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .update({
        ownerName: "Tampered",
        updatedAt: new Date(Date.now() + 60000),
      })
  );

  // Bob cannot read or modify Alice's profile
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .update({ ownerName: "Bob", updatedAt: new Date(Date.now() - 500) })
  );
});

test("FarmActivity: Kotlin-mirrored create, list, partial update, delete, and negative checks", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  const activitiesRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("activities");

  // Empty list query succeeds on user-scoped subcollection
  await assertSucceeds(activitiesRef.get());

  // Mirror Kotlin ActivityRepository create payload (NO `id` field)
  await assertSucceeds(
    activitiesRef.doc("act_1").set({
      userId: ALICE_UID,
      recordType: "SALE",
      title: "Sold 20 bags of maize to Mr. Ade",
      itemOrCrop: "Maize",
      plotOrUnit: "Plot A",
      quantity: 20,
      unit: "bags",
      totalAmount: 400000,
      amountPaid: 250000,
      paymentStatus: "PARTIAL",
      partyName: "Mr. Ade",
      partyRole: "CUSTOMER",
      activityDate: "2026-11-27",
      notes: "Balance due next week",
      extraFields: [],
      createdAt: created,
      updatedAt: created,
    })
  );

  // Custom record with extraFields succeeds
  await assertSucceeds(
    activitiesRef.doc("act_2").set({
      userId: ALICE_UID,
      recordType: "CUSTOM",
      customTypeName: "Vaccination",
      title: "Vaccinated 200 chickens",
      itemOrCrop: "Broilers",
      plotOrUnit: "Pen 2",
      quantity: 200,
      unit: "chickens",
      totalAmount: 18000,
      amountPaid: 18000,
      paymentStatus: "PAID",
      partyName: "Dr. Musa",
      partyRole: "SUPPLIER",
      activityDate: "2026-11-27",
      notes: "Newcastle booster",
      extraFields: ["Vaccine: Newcastle", "Head Count: 200"],
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject `@DocumentId` `id` leaked into document body
  await assertFails(
    activitiesRef.doc("act_3").set({
      id: "act_3",
      userId: ALICE_UID,
      recordType: "HARVEST",
      title: "Harvested tomatoes",
      itemOrCrop: "Tomatoes",
      plotOrUnit: "Plot B",
      quantity: 5,
      unit: "tons",
      totalAmount: 0,
      amountPaid: 0,
      paymentStatus: "NA",
      partyName: "",
      partyRole: "NONE",
      activityDate: "2026-11-27",
      notes: "",
      extraFields: [],
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial update to record payment received
  await assertSucceeds(
    activitiesRef.doc("act_1").update({
      amountPaid: 400000,
      paymentStatus: "PAID",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Cross-user isolation
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("activities").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("activities")
      .doc("act_1")
      .delete()
  );

  // Owner delete succeeds
  await assertSucceeds(activitiesRef.doc("act_2").delete());
});

test("LearnedMemory, CustomRecordType & ChatMessage: owner CRUD and isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  const memRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("learned_memories");
  await assertSucceeds(
    memRef.doc("mem_1").set({
      userId: ALICE_UID,
      category: "CUSTOMER",
      label: "Mr. Ade",
      detail: "Regular maize buyer",
      unit: "bags",
      typicalPrice: 20000,
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(
    memRef.doc("mem_1").update({
      detail: "Regular maize and tomato buyer",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(memRef.get());
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("learned_memories")
      .doc("mem_1")
      .get()
  );

  const customRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("custom_record_types");
  await assertSucceeds(
    customRef.doc("type_vac").set({
      userId: ALICE_UID,
      name: "Vaccination",
      description: "Livestock vaccination tracking",
      fieldNames: ["Animal Group", "Vaccine", "Head Count", "Cost"],
      createdAt: created,
      updatedAt: created,
    })
  );

  const chatRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("chat_messages");
  await assertSucceeds(
    chatRef.doc("msg_1").set({
      userId: ALICE_UID,
      sender: "BUDDY",
      text: "Good morning Ada! Anything happening on the farm today?",
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("chat_messages").get()
  );
});
