/*
 *  Copyright (C) 2010 Ryszard Wiśniewski <brut.alll@gmail.com>
 *  Copyright (C) 2010 Connor Tumbleson <connor.tumbleson@gmail.com>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package brut.androlib.smali;

import brut.androlib.exceptions.AndrolibException;
import brut.util.OS;
import com.android.tools.smali.baksmali.Baksmali;
import com.android.tools.smali.baksmali.BaksmaliOptions;
import com.android.tools.smali.dexlib2.analysis.InlineMethodResolver;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedOdexFile;
import com.android.tools.smali.dexlib2.dexbacked.ZipDexContainer;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class SmaliDecoder {
    private static final int MAX_BAKSMALI_JOBS = 6;

    private final ZipDexContainer mDexContainer;
    private final boolean mDebugMode;
    private final Set<String> mDexFiles;
    private final AtomicInteger mInferredApiLevel;

    public SmaliDecoder(File apkFile, boolean debugMode) throws AndrolibException {
        mDexContainer = new ZipDexContainer(apkFile, null);
        // ZipDexContainer is lazily initialized and not thread-safe. Eagerly initialize on the constructing thread.
        try {
            mDexContainer.getEntry("");
        } catch (IOException ex) {
            throw new AndrolibException("Could not open apk file: " + apkFile, ex);
        }
        mDebugMode = debugMode;
        mDexFiles = ConcurrentHashMap.newKeySet();
        mInferredApiLevel = new AtomicInteger();
    }

    public Set<String> getDexFiles() {
        return mDexFiles;
    }

    public int getInferredApiLevel() {
        return mInferredApiLevel.get();
    }

    public void decode(String dexName, File outDir) throws AndrolibException {
        try {
            Map<Integer, DexBackedDexFile> dexFiles = collectDexFiles(dexName);

            for (Map.Entry<Integer, DexBackedDexFile> entry : dexFiles.entrySet()) {
                int dexNum = entry.getKey();
                DexBackedDexFile dexFile = entry.getValue();

                if (dexFile.supportsOptimizedOpcodes()) {
                    throw new AndrolibException("Cannot disassemble an odex file without deodexing it: " + dexName);
                }

                String dirName = resolveSmaliDirName(dexName, dexNum);
                decodeFile(dexFile, new File(outDir, dirName));
            }

            mDexFiles.add(dexName);
        } catch (IOException ex) {
            throw new AndrolibException("Could not baksmali file: " + dexName, ex);
        }
    }

    private Map<Integer, DexBackedDexFile> collectDexFiles(String dexName) throws IOException, AndrolibException {
        ZipDexContainer.DexEntry<DexBackedDexFile> dexEntry = mDexContainer.getEntry(dexName);
        if (dexEntry == null) {
            throw new AndrolibException("Could not find file: " + dexName);
        }

        Map<Integer, DexBackedDexFile> dexFiles = new TreeMap<>();
        dexFiles.put(1, dexEntry.getDexFile());

        collectMultiDexFiles(dexName, dexFiles);

        return dexFiles;
    }

    private void collectMultiDexFiles(String dexName, Map<Integer, DexBackedDexFile> dexFiles) throws IOException {
        String prefix = dexName + "/";

        for (String dexEntryName : mDexContainer.getDexEntryNames()) {
            if (dexEntryName.equals(dexName) || !dexEntryName.startsWith(prefix)) {
                continue;
            }

            int dexNum = parseDexNum(dexEntryName.substring(prefix.length()));
            if (dexNum > 1) {
                dexFiles.put(dexNum, mDexContainer.getEntry(dexEntryName).getDexFile());
            }
        }
    }

    private int parseDexNum(String numStr) {
        try {
            return Integer.parseInt(numStr);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    static String resolveSmaliDirName(String dexName, int dexNum) {
        String dirName = "smali";
        if (dexNum > 1 || !dexName.equals("classes.dex")) {
            dirName += "_" + dexName.substring(0, dexName.lastIndexOf('.')).replace('/', '@');
            if (dexNum > 1) {
                dirName += dexNum;
            }
        }
        return dirName;
    }

    private void decodeFile(DexBackedDexFile dexFile, File smaliDir) {
        int jobs = Math.min(Runtime.getRuntime().availableProcessors(), MAX_BAKSMALI_JOBS);

        BaksmaliOptions options = createBaksmaliOptions(dexFile);

        OS.mkdir(smaliDir);
        Baksmali.disassembleDexFile(dexFile, smaliDir, jobs, options);

        updateInferredApiLevel(dexFile);
    }

    private BaksmaliOptions createBaksmaliOptions(DexBackedDexFile dexFile) {
        BaksmaliOptions options = new BaksmaliOptions();
        options.parameterRegisters = true;
        options.localsDirective = true;
        options.sequentialLabels = true;
        options.debugInfo = mDebugMode;
        options.codeOffsets = false;
        options.accessorComments = false;
        options.allowOdex = false;
        options.deodex = false;
        options.implicitReferences = false;
        options.normalizeVirtualMethods = false;
        options.registerInfo = 0;

        if (dexFile instanceof DexBackedOdexFile) {
            options.inlineResolver = InlineMethodResolver.createInlineMethodResolver(
                ((DexBackedOdexFile) dexFile).getOdexVersion());
        }

        return options;
    }

    private void updateInferredApiLevel(DexBackedDexFile dexFile) {
        int apiLevel = dexFile.getOpcodes().api;
        mInferredApiLevel.updateAndGet(cur -> (cur == 0 || cur > apiLevel) ? apiLevel : cur);
    }
}
