import { defineConfig } from "vitest/config";

/**
 * Configuration of the tests of the client side scripts (same setup as Jenkins core). The test results are written
 * to the folder of the Maven Surefire reports, so they are shown in Jenkins together with the Java tests.
 */
export default defineConfig({
  test: {
    environment: "jsdom",
    include: ["src/test/js/**/*.test.js"],
    reporters: ["default", "junit"],
    outputFile: { junit: "target/surefire-reports/TEST-vitest.xml" },
  },
});
