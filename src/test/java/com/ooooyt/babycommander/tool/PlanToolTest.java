package com.ooooyt.babycommander.tool;

import com.ooooyt.babycommander.ui.UiEvent;
import com.ooooyt.babycommander.util.I18n;
import io.vertx.mutiny.core.eventbus.EventBus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PlanToolTest {

    @Mock
    EventBus eventBus;

    private PlanTool planTool;

    @BeforeAll
    static void setupLocale() {
        I18n.setLocale(Locale.ENGLISH);
    }

    @BeforeEach
    void setUp() {
        planTool = new PlanTool(eventBus);
    }

    @Test
    void testCreatePlan_WithValidInput() {
        String result = planTool.createPlan("Test task", new String[]{"Phase 1", "Phase 2", "Phase 3"});
        assertNotNull(result);
        assertTrue(planTool.hasPhases());
        assertTrue(planTool.hasInProgressPhases());
    }

    @Test
    void testCreatePlan_FirstPhaseIsActive() {
        planTool.createPlan("Task", new String[]{"First", "Second"});
        List<UiEvent.Phase> phases = planTool.getPhases();
        assertEquals(2, phases.size());
        assertEquals("active", phases.get(0).status());
        assertEquals("pending", phases.get(1).status());
    }

    @Test
    void testCreatePlan_RejectsWhenInProgress() {
        planTool.createPlan("Task", new String[]{"Phase 1", "Phase 2"});
        String result = planTool.createPlan("New task", new String[]{"New phase"});
        assertTrue(result.contains("in progress") || result.contains("Cannot"));
    }

    @Test
    void testCreatePlan_WithNullTask() {
        String result = planTool.createPlan(null, new String[]{"Phase 1"});
        assertNotNull(result);
    }

    @Test
    void testCreatePlan_WithEmptyPhases() {
        String result = planTool.createPlan("Task", new String[]{});
        assertNotNull(result);
    }

    @Test
    void testCompletePhase_ValidPhase() {
        planTool.createPlan("Task", new String[]{"Phase 1", "Phase 2"});
        String result = planTool.completePhase(1);
        assertNotNull(result);
        List<UiEvent.Phase> phases = planTool.getPhases();
        assertEquals("completed", phases.get(0).status());
        assertEquals("active", phases.get(1).status());
    }

    @Test
    void testCompletePhase_LastPhase() {
        planTool.createPlan("Task", new String[]{"Only phase"});
        String result = planTool.completePhase(1);
        assertNotNull(result);
        List<UiEvent.Phase> phases = planTool.getPhases();
        assertEquals("completed", phases.get(0).status());
        assertFalse(planTool.hasInProgressPhases());
    }

    @Test
    void testCompletePhase_InvalidPhaseNumber_TooLow() {
        planTool.createPlan("Task", new String[]{"Phase 1"});
        String result = planTool.completePhase(0);
        assertNotNull(result);
    }

    @Test
    void testCompletePhase_InvalidPhaseNumber_TooHigh() {
        planTool.createPlan("Task", new String[]{"Phase 1"});
        String result = planTool.completePhase(99);
        assertNotNull(result);
    }

    @Test
    void testCompletePhase_NotActivePhase() {
        planTool.createPlan("Task", new String[]{"Phase 1", "Phase 2"});
        // Phase 2 is pending, not active
        String result = planTool.completePhase(2);
        assertNotNull(result);
        assertTrue(result.contains("not") || result.contains("active"));
    }

    @Test
    void testFailPhase_ValidPhase() {
        planTool.createPlan("Task", new String[]{"Phase 1", "Phase 2"});
        String result = planTool.failPhase(1);
        assertNotNull(result);
        List<UiEvent.Phase> phases = planTool.getPhases();
        assertEquals("failed", phases.get(0).status());
    }

    @Test
    void testFailPhase_InvalidPhaseNumber() {
        planTool.createPlan("Task", new String[]{"Phase 1"});
        String result = planTool.failPhase(99);
        assertNotNull(result);
    }

    @Test
    void testFailPhase_NotActivePhase() {
        planTool.createPlan("Task", new String[]{"Phase 1", "Phase 2"});
        String result = planTool.failPhase(2);
        assertNotNull(result);
        List<UiEvent.Phase> phases = planTool.getPhases();
        assertEquals("failed", phases.get(1).status());
    }

    @Test
    void testCompleteAllRemainingPhases() {
        planTool.createPlan("Task", new String[]{"Phase 1", "Phase 2", "Phase 3"});
        planTool.completePhase(1);
        planTool.completeAllRemainingPhases();
        List<UiEvent.Phase> phases = planTool.getPhases();
        assertEquals("completed", phases.get(0).status());
        assertEquals("completed", phases.get(1).status());
        assertEquals("completed", phases.get(2).status());
        assertFalse(planTool.hasInProgressPhases());
    }

    @Test
    void testCompleteAllRemainingPhases_NoInProgress() {
        planTool.createPlan("Task", new String[]{"Phase 1", "Phase 2"});
        planTool.completeAllRemainingPhases();
        List<UiEvent.Phase> phases = planTool.getPhases();
        assertEquals("completed", phases.get(0).status());
        assertEquals("completed", phases.get(1).status());
    }

    @Test
    void testHasPhases_Initially() {
        assertFalse(planTool.hasPhases());
    }

    @Test
    void testHasInProgressPhases_Initially() {
        assertFalse(planTool.hasInProgressPhases());
    }

    @Test
    void testGetLatestSnapshot() {
        planTool.createPlan("Task", new String[]{"Phase 1"});
        List<UiEvent.Phase> snapshot = PlanTool.getLatestSnapshot();
        assertNotNull(snapshot);
        assertEquals(1, snapshot.size());
    }

    @Test
    void testGetLatestSnapshot_ReturnsCopy() {
        planTool.createPlan("Task", new String[]{"Phase 1"});
        List<UiEvent.Phase> snapshot = PlanTool.getLatestSnapshot();
        assertNotNull(snapshot);
        assertEquals(1, snapshot.size());
        assertEquals("Phase 1", snapshot.get(0).description());
    }

    @Test
    void testGetLatestTask() {
        planTool.createPlan("My Task", new String[]{"Phase 1"});
        assertEquals("My Task", PlanTool.getLatestTask());
    }

    @Test
    void testGetLatestTask_WithNullTask() {
        String result = planTool.createPlan(null, new String[]{"Phase 1"});
        assertNotNull(result);
        assertTrue(result.contains("task"));
    }

    @Test
    void testGetPhases_WithoutPhases() {
        List<UiEvent.Phase> phases = planTool.getPhases();
        assertNotNull(phases);
        assertTrue(phases.isEmpty());
    }

    @Test
    void testFailAllRemainingPhases_WithActiveAndPending() {
        planTool.createPlan("Task", new String[]{"Phase 1", "Phase 2", "Phase 3"});
        planTool.completePhase(1);
        // Now: phase1=completed, phase2=active, phase3=pending
        planTool.failAllRemainingPhases();
        List<UiEvent.Phase> phases = planTool.getPhases();
        assertEquals("completed", phases.get(0).status());
        assertEquals("failed", phases.get(1).status());
        assertEquals("failed", phases.get(2).status());
        assertFalse(planTool.hasInProgressPhases());
    }

    @Test
    void testFailAllRemainingPhases_AllCompleted() {
        planTool.createPlan("Task", new String[]{"Phase 1"});
        planTool.completePhase(1);
        // All phases already completed
        planTool.failAllRemainingPhases();
        List<UiEvent.Phase> phases = planTool.getPhases();
        assertEquals("completed", phases.get(0).status());
    }

    @Test
    void testFailAllRemainingPhases_NoPhases() {
        // No plan created yet
        planTool.failAllRemainingPhases();
        List<UiEvent.Phase> phases = planTool.getPhases();
        assertTrue(phases.isEmpty());
    }

    @Test
    void testFailAllRemainingPhases_AllFailed() {
        planTool.createPlan("Task", new String[]{"Phase 1", "Phase 2"});
        planTool.failPhase(1);
        // Now: phase1=failed, phase2=pending
        planTool.failAllRemainingPhases();
        List<UiEvent.Phase> phases = planTool.getPhases();
        assertEquals("failed", phases.get(0).status());
        assertEquals("failed", phases.get(1).status());
        assertFalse(planTool.hasInProgressPhases());
    }


    @Test
    void testCreatePlan_WithPhaseInput_TitleAndDescription() {
        PlanTool.PhaseInput[] inputs = new PlanTool.PhaseInput[]{
            new PlanTool.PhaseInput("Setup", "Initialize the project structure"),
            new PlanTool.PhaseInput("Build", "Compile and run tests")
        };
        String result = planTool.createPlan("Task", inputs);
        assertNotNull(result);
        List<UiEvent.Phase> phases = planTool.getPhases();
        assertEquals(2, phases.size());
        assertEquals("Setup", phases.get(0).title());
        assertEquals("Initialize the project structure", phases.get(0).description());
        assertEquals("Build", phases.get(1).title());
        assertEquals("Compile and run tests", phases.get(1).description());
    }

    @Test
    void testCreatePlan_PhaseInput_FallsBackToTitleForDescription() {
        PlanTool.PhaseInput[] inputs = new PlanTool.PhaseInput[]{
            new PlanTool.PhaseInput("Only title")
        };
        planTool.createPlan("Task", inputs);
        List<UiEvent.Phase> phases = planTool.getPhases();
        assertEquals("Only title", phases.get(0).title());
        assertEquals("Only title", phases.get(0).description());
    }

    @Test
    void testCreatePlan_TitleTruncatedToMaxWords() {
        String longTitle = "This is a very long phase title that definitely exceeds ten words";
        PlanTool.PhaseInput[] inputs = new PlanTool.PhaseInput[]{
            new PlanTool.PhaseInput(longTitle, "desc")
        };
        planTool.createPlan("Task", inputs);
        List<UiEvent.Phase> phases = planTool.getPhases();
        assertEquals(10, phases.get(0).title().split(" ").length);
    }

    @Test
    void testCreatePlan_DescriptionTruncatedToMaxWords() {
        String longDesc = "one two three four five six seven eight nine ten eleven twelve "
            + "thirteen fourteen fifteen sixteen seventeen eighteen nineteen twenty "
            + "twenty-one twenty-two twenty-three twenty-four twenty-five twenty-six "
            + "twenty-seven twenty-eight twenty-nine thirty thirty-one";
        PlanTool.PhaseInput[] inputs = new PlanTool.PhaseInput[]{
            new PlanTool.PhaseInput("Title", longDesc)
        };
        planTool.createPlan("Task", inputs);
        List<UiEvent.Phase> phases = planTool.getPhases();
        assertEquals(30, phases.get(0).description().split(" ").length);
    }

    @Test
    void testCreatePlan_DuplicateTitlesAreDisambiguated() {
        PlanTool.PhaseInput[] inputs = new PlanTool.PhaseInput[]{
            new PlanTool.PhaseInput("Same", "first"),
            new PlanTool.PhaseInput("Same", "second"),
            new PlanTool.PhaseInput("Same", "third")
        };
        planTool.createPlan("Task", inputs);
        List<UiEvent.Phase> phases = planTool.getPhases();
        assertEquals("Same", phases.get(0).title());
        assertEquals("Same (2)", phases.get(1).title());
        assertEquals("Same (3)", phases.get(2).title());
    }

}
