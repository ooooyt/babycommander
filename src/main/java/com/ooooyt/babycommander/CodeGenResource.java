package com.ooooyt.babycommander;

import com.ooooyt.babycommander.orchestrator.Orchestrator;
import com.ooooyt.babycommander.status.RestStatusTracker;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import com.ooooyt.babycommander.util.I18n;
import com.ooooyt.babycommander.util.MessageKey;
import jakarta.ws.rs.core.Response;

import java.util.LinkedHashMap;
import java.util.Map;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CodeGenResource {

    @Inject
    Orchestrator orchestrator;

    @Inject
    RestStatusTracker statusTracker;

    @GET
    @Path("/status")
    public Response status() {
        var state = statusTracker.getState();
        var response = new LinkedHashMap<String, Object>();
        response.put("active", state.active);
        response.put("status", state.status);
        response.put("workflowType", state.workflowType != null ? state.workflowType : I18n.tr(MessageKey.RESOURCE_N_A));
        response.put("workspace", orchestrator.getWorkspace() != null ? orchestrator.getWorkspace() : I18n.tr(MessageKey.RESOURCE_NOT_INITIALIZED));
        response.put("currentStep", state.currentStep != null ? state.currentStep : I18n.tr(MessageKey.RESOURCE_NONE));
        response.put("currentStepRole", state.currentStepRole != null ? state.currentStepRole : I18n.tr(MessageKey.RESOURCE_NONE));
        response.put("currentStepStatus", state.currentStepStatus != null ? state.currentStepStatus : I18n.tr(MessageKey.RESOURCE_NONE));
        response.put("currentTool", state.currentToolName != null ? state.currentToolName : I18n.tr(MessageKey.RESOURCE_IDLE));
        response.put("currentToolInput", state.currentToolInput != null ? state.currentToolInput : "");
        response.put("completedSteps", state.completedSteps);
        response.put("totalDurationMs", state.totalDurationMs);
        response.put("recentEvents", state.recentEvents.stream()
            .map(e -> Map.of("type", e.type, "timestamp", e.timestamp))
            .toList());
        response.put("version", I18n.tr(MessageKey.RESOURCE_VERSION));
        return Response.ok(response).build();
    }
}
