import SwiftUI
import UIKit
import TrueFindShared

struct ActivityShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    var onComplete: (() -> Void)? = nil

    func makeUIViewController(context: Context) -> UIActivityViewController {
        let vc = UIActivityViewController(activityItems: items, applicationActivities: nil)
        vc.completionWithItemsHandler = { _, _, _, _ in
            onComplete?()
        }
        return vc
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}

struct ChooserSheetView: View {
    let initialPayload: IosCapturePayload
    let onOpenUrl: ((URL) -> Void)?
    let onDismiss: () -> Void

    @State private var currentPayload: IosCapturePayload
    @State private var templates: [PromptTemplate] = []
    @State private var selectedTemplateId: String = ""
    @State private var promptDraft: String = ""
    @State private var promptExpanded: Bool = false
    @State private var showCropSheet: Bool = false
    @State private var showShareSheet: Bool = false
    @State private var shareItems: [Any] = []
    @State private var toastMessage: String? = nil

    private let targets: [AiTargetApp] = KnownAiTargets.shared.ALL

    init(
        payload: IosCapturePayload,
        onOpenUrl: ((URL) -> Void)?,
        onDismiss: @escaping () -> Void
    ) {
        self.initialPayload = payload
        self.onOpenUrl = onOpenUrl
        self.onDismiss = onDismiss
        _currentPayload = State(initialValue: payload)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    previewCard

                    VStack(alignment: .leading, spacing: 8) {
                        Text("1. 查核類型")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(templates, id: \.id) { tpl in
                                    let isSelected = tpl.id == selectedTemplateId
                                    Button {
                                        selectedTemplateId = tpl.id
                                        promptDraft = tpl.body
                                        TrueFindSharedContainer.shared.lastTemplateId = tpl.id
                                    } label: {
                                        Text(tpl.title)
                                            .font(.subheadline.weight(isSelected ? .semibold : .regular))
                                            .padding(.horizontal, 12)
                                            .padding(.vertical, 7)
                                            .background(isSelected ? Color.accentColor : Color(.secondarySystemBackground))
                                            .foregroundStyle(isSelected ? Color.white : Color.primary)
                                            .clipShape(Capsule())
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                        }
                    }

                    VStack(alignment: .leading, spacing: 6) {
                        if promptExpanded {
                            Text("本次查核指令（可微調）")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            TextEditor(text: $promptDraft)
                                .frame(minHeight: 96)
                                .padding(8)
                                .background(Color(.secondarySystemBackground))
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                        } else {
                            Button {
                                promptExpanded = true
                            } label: {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(promptDraft)
                                        .font(.footnote)
                                        .foregroundStyle(.secondary)
                                        .lineLimit(2)
                                    Text("點此展開微調指令")
                                        .font(.caption.weight(.medium))
                                        .foregroundStyle(Color.accentColor)
                                }
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(12)
                                .background(Color(.secondarySystemBackground))
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                            }
                            .buttonStyle(.plain)
                        }
                    }

                    VStack(alignment: .leading, spacing: 10) {
                        Text("2. 點選 AI 立即送出")
                            .font(.caption)
                            .foregroundStyle(.secondary)

                        // Primary share button (carries cropped/stitched image + prompt to ChatGPT / Gemini)
                        Button {
                            shareItems = IosAiDispatcher.buildShareItems(promptBody: promptDraft, payload: currentPayload)
                            toastMessage = "查核指令已同步複製到剪貼簿；若送給 Gemini 未自動帶字，點輸入框貼上即可"
                            showShareSheet = true
                        } label: {
                            HStack {
                                Image(systemName: "square.and.arrow.up")
                                Text(currentPayload.hasImage ? "帶截圖＋指令送給 ChatGPT / Gemini" : "透過系統分享送給 AI App")
                                    .fontWeight(.semibold)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 13)
                        }
                        .buttonStyle(.borderedProminent)

                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 14) {
                                ForEach(targets, id: \.id) { target in
                                    Button {
                                        let finalPrompt = IosAiDispatcher.composeAndCopyPrompt(
                                            promptBody: promptDraft,
                                            payload: currentPayload
                                        )
                                        if target.id == "gemini" && currentPayload.hasImage {
                                            toastMessage = "圖片已送往 Gemini，查核指令已複製 — 點輸入框貼上即可"
                                            shareItems = IosAiDispatcher.buildShareItems(promptBody: promptDraft, payload: currentPayload)
                                            showShareSheet = true
                                        } else if !currentPayload.hasImage,
                                                  let url = IosAiDispatcher.buildDirectUrl(
                                                      for: target,
                                                      finalPrompt: finalPrompt,
                                                      hasImage: false
                                                  ),
                                                  let open = onOpenUrl {
                                            toastMessage = "已複製查核指令，正開啟 \(target.displayName)…"
                                            open(url)
                                            onDismiss()
                                        } else {
                                            toastMessage = "查核指令已複製，請在下方點選 \(target.displayName)"
                                            shareItems = IosAiDispatcher.buildShareItems(promptBody: promptDraft, payload: currentPayload)
                                            showShareSheet = true
                                        }
                                    } label: {
                                        VStack(spacing: 6) {
                                            ZStack {
                                                RoundedRectangle(cornerRadius: 14)
                                                    .fill(Color(hex: UInt64(target.accentColorHex)))
                                                    .frame(width: 48, height: 48)
                                                Text(target.badgeText)
                                                    .font(.system(size: 14, weight: .heavy))
                                                    .foregroundStyle(.white)
                                            }
                                            Text(target.displayName)
                                                .font(.caption2)
                                                .foregroundStyle(.primary)
                                        }
                                        .frame(width: 60)
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                        }
                    }

                    if let toast = toastMessage {
                        Text(toast)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .padding(10)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color(.tertiarySystemBackground))
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                    }
                }
                .padding(18)
            }
            .navigationTitle("TrueFind 事實查核")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("關閉", action: onDismiss)
                }
            }
            .onAppear {
                let loaded = TrueFindSharedContainer.shared.templateRepository.getAll()
                templates = loaded
                let lastId = TrueFindSharedContainer.shared.lastTemplateId
                let initial = loaded.first(where: { $0.id == lastId }) ?? loaded.first
                if let initial {
                    selectedTemplateId = initial.id
                    promptDraft = initial.body
                }
            }
            .sheet(isPresented: $showShareSheet) {
                ActivityShareSheet(items: shareItems) {
                    onDismiss()
                }
            }
            .fullScreenCover(isPresented: $showCropSheet) {
                if let img = currentPayload.resolvedImage() {
                    IosCropSheetView(
                        image: img,
                        onCancel: { showCropSheet = false },
                        onConfirm: { cropped in
                            currentPayload = IosCapturePayload(
                                image: cropped,
                                imageUrl: nil,
                                text: currentPayload.text,
                                sourceLabel: currentPayload.sourceLabel,
                                frameCount: currentPayload.frameCount
                            )
                            showCropSheet = false
                        }
                    )
                }
            }
        }
    }

    @ViewBuilder
    private var previewCard: some View {
        HStack(spacing: 12) {
            if let img = currentPayload.resolvedImage() {
                Image(uiImage: img)
                    .resizable()
                    .scaledToFill()
                    .frame(width: 58, height: 76)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
            }
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 6) {
                    Text(currentPayload.sourceLabel)
                        .font(.subheadline.weight(.semibold))
                    if currentPayload.frameCount > 1 {
                        Text("已拼接 \(currentPayload.frameCount) 頁")
                            .font(.caption2.weight(.bold))
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Color.purple.opacity(0.16))
                            .foregroundStyle(Color.purple)
                            .clipShape(Capsule())
                    }
                }
                if let text = currentPayload.text, !text.isEmpty {
                    Text(text)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(3)
                } else {
                    Text("可先裁切重點，或直接點下方 AI 送出")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            Spacer(minLength: 0)
            if currentPayload.hasImage {
                Button("✂ 裁切") {
                    showCropSheet = true
                }
                .font(.caption.weight(.semibold))
                .buttonStyle(.bordered)
            }
        }
        .padding(12)
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }
}

private extension Color {
    init(hex: UInt64) {
        let r = Double((hex >> 16) & 0xFF) / 255.0
        let g = Double((hex >> 8) & 0xFF) / 255.0
        let b = Double(hex & 0xFF) / 255.0
        self.init(red: r, green: g, blue: b)
    }
}
