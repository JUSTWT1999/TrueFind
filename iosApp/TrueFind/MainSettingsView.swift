import PhotosUI
import SwiftUI
import UIKit
import TrueFindShared

struct MainSettingsView: View {
    @Binding var activePayload: IosCapturePayload?

    @State private var templates: [PromptTemplate] = []
    @State private var installedIds: Set<String> = []
    @State private var selectedPhotoItems: [PhotosPickerItem] = []
    @State private var isStitchingPhotos = false
    @State private var showEditor = false
    @State private var editingTemplate: PromptTemplate? = nil
    @State private var draftTitle = ""
    @State private var draftBody = ""

    private let targets: [AiTargetApp] = KnownAiTargets.shared.ALL

    var body: some View {
        NavigationStack {
            List {
                Section("一鍵截圖查核（iPhone 專用設定）") {
                    VStack(alignment: .leading, spacing: 8) {
                        Label("背面輕點兩下／動作按鈕（最接近懸浮球）", systemImage: "hand.tap.fill")
                            .font(.subheadline.weight(.semibold))
                        Text("1. 打開 iPhone「捷徑」App → 新增捷徑\n2. 加入動作「拍攝截圖」→ 再加入「TrueFind：事實查核截圖」\n3. 前往「設定 → 輔助使用 → 觸控 → 背面輕點 → 點兩下」（或動作按鈕）綁定該捷徑。")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    .padding(.vertical, 4)

                    VStack(alignment: .leading, spacing: 6) {
                        Label("系統分享延伸功能（Share Extension）", systemImage: "square.and.arrow.up")
                            .font(.subheadline.weight(.semibold))
                        Text("在 Threads / FB / IG / Safari 貼文或截圖（支援一次選 1～5 張自動拼成長圖）點「分享 → TrueFind」即可跳出查核面板。")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    .padding(.vertical, 4)

                    PhotosPicker(
                        selection: $selectedPhotoItems,
                        maxSelectionCount: 5,
                        matching: .images
                    ) {
                        HStack {
                            Image(systemName: "photo.stack")
                            Text(isStitchingPhotos ? "正在拼接長截圖…" : "從相簿挑選 1～5 張截圖拼接查核（免相簿權限）")
                                .font(.subheadline.weight(.medium))
                        }
                    }
                    .disabled(isStitchingPhotos)
                    .onChange(of: selectedPhotoItems) { newItems in
                        guard !newItems.isEmpty else { return }
                        loadAndStitchPhotos(items: newItems)
                    }
                }

                Section("本機已偵測 AI App") {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 110))], spacing: 8) {
                        ForEach(targets, id: \.id) { target in
                            let installed = installedIds.contains(target.id)
                            HStack(spacing: 6) {
                                Circle()
                                    .fill(installed ? Color.green : Color.gray.opacity(0.4))
                                    .frame(width: 8, height: 8)
                                Text(installed ? "\(target.displayName) ✓" : target.displayName)
                                    .font(.caption)
                                    .foregroundStyle(installed ? .primary : .secondary)
                            }
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color(.secondarySystemBackground))
                            .clipShape(Capsule())
                        }
                    }
                    .padding(.vertical, 4)
                }

                Section {
                    ForEach(templates, id: \.id) { tpl in
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text(tpl.title)
                                    .font(.subheadline.weight(.semibold))
                                Spacer()
                                Button("編輯") {
                                    editingTemplate = tpl
                                    draftTitle = tpl.title
                                    draftBody = tpl.body
                                    showEditor = true
                                }
                                .font(.caption)
                            }
                            Text(tpl.body)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                                .lineLimit(2)
                        }
                        .padding(.vertical, 4)
                        .swipeActions(edge: .trailing) {
                            if templates.count > 1 {
                                Button("刪除", role: .destructive) {
                                    templates = TrueFindSharedContainer.shared.templateRepository.delete(id: tpl.id)
                                }
                            }
                        }
                    }
                } header: {
                    HStack {
                        Text("事實查核 Prompt 模板")
                        Spacer()
                        Button("還原預設") {
                            templates = TrueFindSharedContainer.shared.templateRepository.resetToDefaults()
                        }
                        .font(.caption)
                    }
                }

                Section("隱私保證") {
                    Text("TrueFind 不連線任何伺服器、不存取系統相簿；截圖僅暫存於 App Group 私有快取並在 1 小時後自動清除。")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("TrueFind")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        editingTemplate = nil
                        draftTitle = ""
                        draftBody = ""
                        showEditor = true
                    } label: {
                        Image(systemName: "plus")
                    }
                }
            }
            .onAppear {
                refreshState()
            }
            .sheet(isPresented: $showEditor) {
                NavigationStack {
                    Form {
                        TextField("模板名稱（例如：查財經數據）", text: $draftTitle)
                        Section("查核指令") {
                            TextEditor(text: $draftBody)
                                .frame(minHeight: 140)
                        }
                    }
                    .navigationTitle(editingTemplate == nil ? "新增模板" : "編輯模板")
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button("取消") { showEditor = false }
                        }
                        ToolbarItem(placement: .confirmationAction) {
                            Button("儲存") {
                                templates = TrueFindSharedContainer.shared.templateRepository.upsert(
                                    title: draftTitle,
                                    body: draftBody,
                                    existingId: editingTemplate?.id
                                )
                                showEditor = false
                            }
                            .disabled(draftTitle.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
                                      draftBody.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                        }
                    }
                }
            }
            .sheet(item: $activePayload) { payload in
                ChooserSheetView(
                    payload: payload,
                    onOpenUrl: { url in
                        UIApplication.shared.open(url)
                    },
                    onDismiss: {
                        activePayload = nil
                    }
                )
            }
        }
    }

    private func refreshState() {
        templates = TrueFindSharedContainer.shared.templateRepository.getAll()
        var found: Set<String> = []
        for target in targets {
            if let url = URL(string: target.iosUrlScheme), UIApplication.shared.canOpenURL(url) {
                found.insert(target.id)
            }
        }
        installedIds = found
    }

    private func loadAndStitchPhotos(items: [PhotosPickerItem]) {
        isStitchingPhotos = true
        Task {
            var loaded: [UIImage] = []
            for item in items {
                if let data = try? await item.loadTransferable(type: Data.self),
                   let img = UIImage(data: data) {
                    loaded.append(img)
                }
            }
            let stitched: UIImage? = await Task.detached(priority: .userInitiated) {
                guard !loaded.isEmpty else { return nil }
                return loaded.count > 1 ? IosImageStitcher.stitch(frames: loaded) : loaded[0]
            }.value

            await MainActor.run {
                isStitchingPhotos = false
                selectedPhotoItems = []
                if let stitched {
                    activePayload = IosCapturePayload(
                        image: stitched,
                        imageUrl: nil,
                        text: nil,
                        sourceLabel: loaded.count > 1 ? "已自動拼接長截圖" : "已挑選截圖",
                        frameCount: loaded.count
                    )
                }
            }
        }
    }
}
