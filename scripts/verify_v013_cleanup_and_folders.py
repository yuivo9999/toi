from pathlib import Path
root = Path(__file__).resolve().parents[1]
image_dao = (root / 'app/src/main/java/com/toimg/app/data/local/dao/ImageDao.kt').read_text()
recognition_dao = (root / 'app/src/main/java/com/toimg/app/data/local/dao/RecognitionDao.kt').read_text()
fts_dao = (root / 'app/src/main/java/com/toimg/app/data/local/dao/ImageFtsDao.kt').read_text()
cleanup = (root / 'app/src/main/java/com/toimg/app/data/repository/LegacyIndexCleanupRepository.kt').read_text()
picker_vm = (root / 'app/src/main/java/com/toimg/app/feature/picker/ImagePickerViewModel.kt').read_text()
picker_ui = (root / 'app/src/main/java/com/toimg/app/feature/picker/ImagePickerScreen.kt').read_text()
build = (root / 'app/build.gradle.kts').read_text()
checks = {
    'current release version 0.1.29 / code 28': 'versionName = "0.1.30"' in build and 'versionCode = 28' in build,
    'legacy scan candidates match explicit directory segments': all(x in image_dao for x in ["%/.thumbnails/%", "%/thumbnails/%", "%/.cache/%", "%/cache/%", "%/.trash/%", "%/trash/%", "%/temp/%", "%/tmp/%"]),
    'bulk image index deletion DAO exists': 'deleteImagesByIds' in image_dao,
    'bulk recognition deletion DAO exists': 'deleteByImageIds' in recognition_dao,
    'bulk FTS deletion DAO exists': 'deleteFtsByImageIds' in fts_dao,
    'cleanup is transactional': 'withTransaction' in cleanup,
    'cleanup revalidates candidate IDs': 'intersect(preview.imageIds.toSet())' in cleanup,
    'cleanup only mutates database DAOs': all(x in cleanup for x in ['imageFtsDao()', 'recognitionDao()', 'imageDao()']) and 'File.delete' not in cleanup,
    'folder grouping derives stable parent folder key': 'stableFolderKey()' in picker_vm and 'stored == path' in picker_vm,
    'folder-level selection preserves existing selection': 'state.selectedImageIds + ids' in picker_vm,
    'folder cards shown before image grid': 'Folder-first navigation' in picker_ui and '选择整个文件夹' in picker_ui,
    'cleanup preview and explicit confirmation UI exists': '历史索引清理预览' in picker_ui and '确认清理索引' in picker_ui,
}
for label, ok in checks.items():
    print(('PASS' if ok else 'FAIL') + ': ' + label)
if not all(checks.values()):
    raise SystemExit(1)
print(f'PASS: {len(checks)} v0.1.13 checks')
