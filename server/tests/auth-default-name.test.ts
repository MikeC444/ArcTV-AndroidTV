import { describe, expect, it } from "vitest";
import { defaultDisplayName } from "../src/services/authService.js";

describe("defaultDisplayName", () => {
  it("is the first word of the email's local part, capitalised", () => {
    expect(defaultDisplayName("alex.smith@example.com")).toBe("Alex");
    expect(defaultDisplayName("jo_92@example.com")).toBe("Jo");
    expect(defaultDisplayName("MICHAEL@example.com")).toBe("MICHAEL");
  });

  it("is at most 24 characters", () => {
    expect(defaultDisplayName(`${"a".repeat(40)}@example.com`)).toHaveLength(24);
  });

  it("is Me when the local part has no letters or digits", () => {
    expect(defaultDisplayName("...@example.com")).toBe("Me");
    expect(defaultDisplayName("@example.com")).toBe("Me");
  });
});
