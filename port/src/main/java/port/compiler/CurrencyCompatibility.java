package port.compiler;

import java.util.ArrayList;
import java.util.List;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.Instruction;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReference;
import org.teavm.model.Program;
import org.teavm.model.ValueType;
import org.teavm.model.instructions.InvokeInstruction;

/**
 * TeaVM's {@code TNumberFormat.<init>} resolves the currency eagerly through
 * {@code TCurrency.getInstance(TLocale.getDefault())}, and TeaVM's baked ISO-4217 table has no entry
 * for the country its CLDR metadata resolves for a country-less default locale ("en" resolves to CY,
 * and CYP is absent), so it throws {@code IllegalArgumentException: Currency not found: CYP} inside
 * {@code zombie.entity.components.fluids.FluidContainer.<clinit>}.
 *
 * The real JDK 25 was measured for the same call ({@code new DecimalFormat("#.##",
 * DecimalFormatSymbols.getInstance(Locale.ENGLISH))}): the symbols carry currency {@code XXX} and no
 * exception is raised; {@code Currency.getInstance(Locale)} only throws when the locale's country is
 * not a valid ISO 3166 code. This transformer therefore redirects that single call to
 * {@link port.runtime.JdkSupport#currencyForLocale(java.util.Locale)}, which reproduces the JDK
 * answer (the locale's currency when it exists, ISO-4217 {@code XXX} otherwise) and records every
 * fallback in the browser diagnostics instead of hiding the classlib deviation.
 */
public final class CurrencyCompatibility implements ClassHolderTransformer {
    private static final MethodReference TEAVM_CURRENCY =
        new MethodReference("java.util.Currency", "getInstance", ValueType.object("java.util.Locale"),
            ValueType.object("java.util.Currency"));
    private static final MethodReference BRIDGE =
        new MethodReference("port.runtime.JdkSupport", "currencyForLocale", ValueType.object("java.util.Locale"),
            ValueType.object("java.util.Currency"));

    @Override public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
        if (!cls.getName().equals("java.text.NumberFormat")) return;
        var constructor = cls.getMethod(new MethodDescriptor("<init>", ValueType.VOID));
        if (constructor == null) throw new IllegalStateException("java.text.NumberFormat.<init>()V is missing");
        var patched = redirectCurrencyLookups(constructor.getProgram());
        if (patched != 1) throw new IllegalStateException(
            "Expected exactly one Currency.getInstance(Locale) call in java.text.NumberFormat.<init>()V, found " + patched);
    }

    private static int redirectCurrencyLookups(Program program) {
        List<InvokeInstruction> matches = new ArrayList<>();
        for (int block = 0; block < program.basicBlockCount(); block++)
            for (Instruction instruction = program.basicBlockAt(block).getFirstInstruction();
                    instruction != null; instruction = instruction.getNext())
                // TeaVM models a static invoke as InvocationType.VIRTUAL with no instance.
                if (instruction instanceof InvokeInstruction invoke
                        && invoke.getInstance() == null
                        && invoke.getMethod().equals(TEAVM_CURRENCY)) matches.add(invoke);
        for (var invoke : matches) invoke.setMethod(BRIDGE);
        return matches.size();
    }
}
