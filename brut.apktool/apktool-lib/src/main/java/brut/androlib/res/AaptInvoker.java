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
package brut.androlib.res;

import brut.androlib.Config;
import brut.androlib.exceptions.AndrolibException;
import brut.androlib.meta.*;
import brut.androlib.res.Framework;
import brut.common.BrutException;
import brut.common.Log;
import brut.util.OS;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AaptInvoker {
    private static final String TAG = AaptInvoker.class.getName();

    private final ApkInfo mApkInfo;
    private final Config mConfig;

    public AaptInvoker(ApkInfo apkInfo, Config config) {
        mApkInfo = apkInfo;
        mConfig = config;
    }

    public void invoke(File outApk, File manifest, File resDir) throws AndrolibException {
        String aaptPath = resolveAaptPath();

        File resZip = null;
        if (resDir != null) {
            resZip = compileResources(aaptPath, resDir);
        }

        if (manifest == null) {
            return;
        }

        linkResources(aaptPath, outApk, manifest, resZip);

        if (needsOptimization()) {
            optimizeApk(aaptPath, outApk);
        }
    }

    private String resolveAaptPath() throws AndrolibException {
        String aaptPath = mConfig.getAaptBinary();
        if (aaptPath == null || aaptPath.isEmpty()) {
            try {
                aaptPath = AaptManager.getBinaryFile().getPath();
            } catch (AndrolibException ex) {
                aaptPath = AaptManager.getBinaryName();
                Log.w(TAG, aaptPath + ": " + ex.getMessage() + " (defaulting to $PATH binary)");
            }
        }
        return aaptPath;
    }

    private File compileResources(String aaptPath, File resDir) throws AndrolibException {
        File resZip = new File(resDir.getParent(), "build/resources.zip");
        OS.rmfile(resZip);

        List<String> cmd = new ArrayList<>();
        cmd.add(aaptPath);
        cmd.add("compile");

        cmd.add("--dir");
        cmd.add(resDir.getPath());

        // Treats error that used to be valid in aapt1 as warnings in aapt2.
        cmd.add("--legacy");

        cmd.add("-o");
        cmd.add(resZip.getPath());

        if (mConfig.isVerbose()) {
            cmd.add("-v");
        }
        if (mConfig.isNoCrunch()) {
            cmd.add("--no-crunch");
        }

        execAaptCommand(cmd, "compile");
        return resZip;
    }

    private void linkResources(String aaptPath, File outApk, File manifest, File resZip)
            throws AndrolibException {
        List<String> cmd = new ArrayList<>();
        cmd.add(aaptPath);
        cmd.add("link");

        cmd.add("-o");
        cmd.add(outApk.getPath());

        cmd.add("--manifest");
        cmd.add(manifest.getPath());

        addSdkVersionFlags(cmd);
        addVersionInfoFlags(cmd);
        addPackageFlags(cmd);
        addFeatureFlagFlags(cmd);

        // Disable automatic changes.
        cmd.add("--no-auto-version");
        cmd.add("--no-version-vectors");
        cmd.add("--no-version-transitions");
        cmd.add("--no-resource-deduping");
        cmd.add("--no-resource-removal");
        cmd.add("--no-compile-sdk-metadata");

        // #3427 - Ignore stricter parsing during aapt2.
        cmd.add("--warn-manifest-validation");

        for (File includeFile : getIncludeFiles()) {
            cmd.add("-I");
            cmd.add(includeFile.getPath());
        }
        if (mConfig.isVerbose()) {
            cmd.add("-v");
        }
        if (resZip != null) {
            cmd.add(resZip.getPath());
        }

        execAaptCommand(cmd, "link");
    }

    private void addSdkVersionFlags(List<String> cmd) {
        SdkInfo sdkInfo = mApkInfo.getSdkInfo();
        if (sdkInfo.getMinSdkVersion() != null) {
            cmd.add("--min-sdk-version");
            cmd.add(sdkInfo.getMinSdkVersion());
        }
        if (sdkInfo.getTargetSdkVersion() != null) {
            cmd.add("--target-sdk-version");
            cmd.add(sdkInfo.getTargetSdkVersion());
        }
    }

    private void addVersionInfoFlags(List<String> cmd) {
        VersionInfo versionInfo = mApkInfo.getVersionInfo();
        if (versionInfo.getVersionCode() >= 0) {
            cmd.add("--version-code");
            cmd.add(Integer.toString(versionInfo.getVersionCode()));
        }
        if (versionInfo.getVersionName() != null) {
            cmd.add("--version-name");
            cmd.add(versionInfo.getVersionName());
        }
    }

    private void addPackageFlags(List<String> cmd) {
        ResourcesInfo resourcesInfo = mApkInfo.getResourcesInfo();
        if (resourcesInfo.getPackageId() >= 0) {
            int pkgId = resourcesInfo.getPackageId();
            if (pkgId == 0) {
                cmd.add("--shared-lib");
            } else if (pkgId > 1) {
                cmd.add("--package-id");
                cmd.add(Integer.toString(pkgId));
                if (pkgId < 0x7F) {
                    cmd.add("--allow-reserved-package-id");
                }
            }
        }
        if (resourcesInfo.getPackageName() != null) {
            cmd.add("--rename-resources-package");
            cmd.add(resourcesInfo.getPackageName());
        }
        if (resourcesInfo.isSparseEntries()) {
            cmd.add("--enable-sparse-encoding");
        }
        if (resourcesInfo.isCompactEntries()) {
            cmd.add("--enable-compact-entries");
        }
        if (resourcesInfo.isKeepRawValues()) {
            cmd.add("--keep-raw-values");
        }
    }

    private void addFeatureFlagFlags(List<String> cmd) {
        if (!mApkInfo.getFeatureFlags().isEmpty()) {
            List<String> featureFlags = new ArrayList<>();
            for (Map.Entry<String, Boolean> entry : mApkInfo.getFeatureFlags().entrySet()) {
                featureFlags.add(entry.getKey() + "=" + entry.getValue());
            }
            cmd.add("--feature-flags");
            cmd.add(String.join(",", featureFlags));
        }
    }

    private boolean needsOptimization() {
        return mConfig.isShortenResPaths() || mConfig.isEnableSparseEncoding() || mConfig.isCollapseResNames();
    }

    private void optimizeApk(String aaptPath, File outApk) throws AndrolibException {
        Path inputFilePath = new File(outApk.getParent(), outApk.getName() + ".tmp").toPath();
        Path apkFilePath = outApk.toPath();
        try {
            Files.copy(apkFilePath, inputFilePath, StandardCopyOption.REPLACE_EXISTING);
            Files.delete(apkFilePath);
        } catch (IOException e) {
            throw new AndrolibException(e);
        }

        List<String> cmd = new ArrayList<>();
        cmd.add(aaptPath);
        cmd.add("optimize");

        cmd.add("-o");
        cmd.add(apkFilePath.toString());

        if (mConfig.isShortenResPaths()) {
            cmd.add("--shorten-resource-paths");
        }
        if (mConfig.isEnableSparseEncoding()) {
            cmd.add("--enable-sparse-encoding");
        }
        if (mConfig.isCollapseResNames()) {
            cmd.add("--collapse-resource-names");
        }

        cmd.add(inputFilePath.toString());

        execAaptCommand(cmd, "optimize");
    }

    private void execAaptCommand(List<String> cmd, String phase) throws AndrolibException {
        try {
            OS.exec(cmd.toArray(new String[0]));
            Log.d(TAG, "aapt2 " + phase + " command ran: " + cmd.toString());
        } catch (BrutException ex) {
            throw new AndrolibException(ex);
        }
    }

    private List<File> getIncludeFiles() throws AndrolibException {
        List<File> files = new ArrayList<>();
        addFrameworkFiles(files);
        addLibraryFiles(files);
        return files;
    }

    private void addFrameworkFiles(List<File> files) throws AndrolibException {
        UsesFramework usesFramework = mApkInfo.getUsesFramework();
        List<Integer> frameworkIds = usesFramework.getIds();
        if (!frameworkIds.isEmpty()) {
            Framework framework = new Framework(mConfig);
            String tag = usesFramework.getTag();
            for (Integer id : frameworkIds) {
                files.add(framework.getApkFile(id, tag));
            }
        }
    }

    private void addLibraryFiles(List<File> files) {
        List<String> usesLibrary = mApkInfo.getUsesLibrary();
        if (usesLibrary.isEmpty()) {
            return;
        }

        String[] libFiles = mConfig.getLibraryFiles();
        for (String name : usesLibrary) {
            File libFile = resolveLibraryFile(name, libFiles);
            if (libFile != null) {
                files.add(libFile);
            } else {
                Log.w(TAG, "Shared library was not provided: " + name);
            }
        }
    }

    private File resolveLibraryFile(String name, String[] libFiles) {
        if (libFiles == null) {
            return null;
        }
        for (String libEntry : libFiles) {
            String[] parts = libEntry.split(":", 2);
            if (parts.length == 2 && name.equals(parts[0])) {
                return new File(parts[1]);
            }
        }
        return null;
    }
}
