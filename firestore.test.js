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

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
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

test("Unauthenticated user: cannot read products", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("products").get());
});

test("Authenticated user: can create and read their own products", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const productRef = aliceDb.collection("users").doc(ALICE_UID).collection("products").doc("prod_1");
  await assertSucceeds(productRef.set({
    userId: ALICE_UID,
    name: "Aashirvaad Atta 5kg",
    price: 240,
    costPrice: 210,
    stockQuantity: 15,
    unit: "pkt",
    category: "Grocery"
  }));
  await assertSucceeds(productRef.get());
});

test("Authenticated user: cannot read or write another user's products", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(ALICE_UID).collection("products").doc("prod_1").set({
      userId: ALICE_UID,
      name: "Secret Item",
      price: 100,
      stockQuantity: 5
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("products").doc("prod_1").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("products").doc("prod_2").set({
    userId: ALICE_UID,
    name: "Hacked Item",
    price: 50,
    stockQuantity: 1
  }));
});

test("Authenticated user: can create and read customer and udhar transaction", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const custRef = aliceDb.collection("users").doc(ALICE_UID).collection("customers").doc("cust_1");
  await assertSucceeds(custRef.set({
    userId: ALICE_UID,
    name: "Ramesh Kumar",
    phone: "9876543210",
    totalUdhar: 150
  }));

  const txRef = aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_1");
  await assertSucceeds(txRef.set({
    userId: ALICE_UID,
    customerId: "cust_1",
    amount: 150,
    type: "UDHAR",
    note: "Dal and Sugar",
    itemsSummary: "Dal 1kg, Sugar 1kg"
  }));
  await assertSucceeds(txRef.get());
});
