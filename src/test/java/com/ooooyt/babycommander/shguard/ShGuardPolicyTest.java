package com.ooooyt.babycommander.shguard;

import com.ooooyt.babycommander.hook.DangerLevel;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link ShGuardPolicy} overrides and the instance API.
 */
class ShGuardPolicyTest {

    @Test
    void testWriteRedirectAllowlist() {
        // Default policy: /dev/null is allowed.
        assertEquals(DangerLevel.SAFE, ShGuard.analyze("echo x > /dev/null"));
        // But /etc/passwd is still blocked.
        assertEquals(DangerLevel.DANGEROUS, ShGuard.analyze("echo x > /etc/passwd"));
    }

    @Test
    void testAdditiveDangerousCommands() {
        ShGuardPolicy policy = ShGuardPolicy.defaults()
                .withDangerousCommands(Set.of("dangerous-tool"));
        ShGuard guard = ShGuard.withPolicy(policy);
        assertEquals(DangerLevel.DANGEROUS, guard.classify("dangerous-tool --flag").level());
        // Existing dangerous commands still work.
        assertEquals(DangerLevel.DANGEROUS, guard.classify("rm -rf /").level());
    }

    @Test
    void testAdditiveSafeCommands() {
        ShGuardPolicy policy = ShGuardPolicy.defaults()
                .withSafeCommands(Set.of("my-safe-tool"));
        ShGuard guard = ShGuard.withPolicy(policy);
        assertEquals(DangerLevel.SAFE, guard.classify("my-safe-tool --flag").level());
    }

    @Test
    void testStrictUnknownCommand() {
        ShGuardPolicy policy = ShGuardPolicy.builder()
                .strictUnknownCommand(true)
                .build();
        ShGuard guard = ShGuard.withPolicy(policy);
        assertEquals(DangerLevel.DANGEROUS, guard.classify("some-unknown-tool x").level());
    }

    @Test
    void testFoldAssignmentsOff() {
        ShGuardPolicy policy = ShGuardPolicy.builder()
                .foldAssignments(false)
                .build();
        ShGuard guard = ShGuard.withPolicy(policy);
        // Without folding, $CMD is unresolvable -> ASK_ONCE (never SAFE).
        assertEquals(DangerLevel.ASK_ONCE, guard.classify("CMD=rm; $CMD -rf /").level());
    }

    @Test
    void testFlagSudoOff() {
        ShGuardPolicy policy = ShGuardPolicy.builder()
                .flagSudoAlways(false)
                .build();
        ShGuard guard = ShGuard.withPolicy(policy);
        // With flagSudoAlways off, 'sudo' is just an unknown command name
        // (not escalated to DANGEROUS, but not SAFE either).
        assertEquals(DangerLevel.ASK_ONCE, guard.classify("sudo git status").level());
    }

    @Test
    void testDefaultPolicyMatchesLegacySets() {
        ShGuardPolicy p = ShGuardPolicy.defaults();
        assertEquals(DangerLevel.DANGEROUS, ShGuard.withPolicy(p).classify("rm file.txt").level());
        assertEquals(DangerLevel.SAFE, ShGuard.withPolicy(p).classify("ls -la").level());
        assertEquals(DangerLevel.ASK_ONCE, ShGuard.withPolicy(p).classify("unknown-cmd x").level());
    }
}