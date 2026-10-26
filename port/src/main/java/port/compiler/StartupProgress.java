package port.compiler;

import java.util.Set;
import org.teavm.model.*;
import org.teavm.model.instructions.*;

/** Observe exact entry/normal return boundaries without replacing game logic. */
public final class StartupProgress implements ClassHolderTransformer {
    private static final Set<String> METHODS = Set.of(
        "zombie.Lua.LuaManager#init()V",
        "zombie.core.logger.LoggerManager#init()V",
        "zombie.core.physics.Bullet#init()V",
        "zombie.GameWindow#initShared()V",
        "zombie.GameWindow#InitGameThread()V",
        "zombie.GameWindow#init()V",
        "zombie.gameStates.MainScreenState#enter()V",
        "zombie.gameStates.MainScreenState#render()V",
        "org.lwjglx.opengl.Display#init()V",
        "org.lwjglx.opengl.Display#create()V");
    @Override public void transformClass(ClassHolder cls,ClassHolderTransformerContext context) {
        for(var method:cls.getMethods()) {
            String key=cls.getName()+"#"+method.getDescriptor();
            if(!METHODS.contains(key)||method.getProgram()==null)continue;
            Program program=method.getProgram();
            Instruction first=program.basicBlockAt(0).getFirstInstruction();
            if(first==null)throw new IllegalStateException("Empty observed entry: "+key);
            record(program,first,key+":entered");
            for(var block:program.getBasicBlocks()) {
                for(Instruction i=block.getFirstInstruction();i!=null;i=i.getNext()) {
                    if(i instanceof ExitInstruction)record(program,i,key+":returned");
                }
            }
        }
    }
    private static void record(Program program,Instruction before,String label) {
        var value=program.createVariable();
        var constant=new StringConstantInstruction();constant.setConstant(label);constant.setReceiver(value);
        var call=new InvokeInstruction();call.setType(InvocationType.SPECIAL);
        call.setMethod(new MethodReference("port.shim.BrowserProgress","record",ValueType.object("java.lang.String"),ValueType.VOID));
        call.setArguments(value);
        before.insertPrevious(constant);before.insertPrevious(call);
    }
}
