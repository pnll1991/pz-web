package port.compiler;
import org.teavm.model.*;
import org.teavm.model.emit.ProgramEmitter;
/** Keep optional OpenTelemetry absent; fail explicitly if an adapter is selected despite its absence. */
public final class OptionalTelemetry implements ClassHolderTransformer {
    public void transformClass(ClassHolder cls,ClassHolderTransformerContext context){
        if(!java.util.Set.of("io.prometheus.metrics.tracer.agent.OpenTelemetryAgentSpanContext","io.prometheus.metrics.tracer.otel.OpenTelemetrySpanContext").contains(cls.getName()))return;
        for(var method:cls.getMethods())if(method.getProgram()!=null){var emitter=ProgramEmitter.create(method,context.getHierarchy());emitter.construct(UnsupportedOperationException.class,emitter.constant("Optional OpenTelemetry adapter is unavailable in the browser: "+cls.getName()+"."+method.getName())).raise();}
    }
}
