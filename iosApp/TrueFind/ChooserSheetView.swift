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
    let payload: IosCapturePayload
    let onOpenUrl: ((URL) -> Void)?
    let onDismiss: () -> Void

    @State private var templates: [PromptTemplate] = []
    @State private var selectedTemplateId: String = ""
    @State private var promptDraft: String = ""
    @State private var promptExpanded: Bool = false
    @State private var showShareSheet: Bool = false
    @State private var shareItems: [Any] = []
    @State private var toastMessage: String? = nil

    private let targets: [AiTargetApp] = KnownAiTargets.shared.ALL

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    previewCard

                    VStack(alignment: .leading, spacing: 8) {
                        Text("選擇查核模板")
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

                    // Primary share button (carries image + prompt to ChatGPT / Gemini via iOS Share Sheet)
                    Button {
                        shareItems = IosAiDispatcher.buildShareItems(promptBody: promptDraft, payload: payload)
                        toastMessage = "查核指令已同步複製到剪貼簿，請在下方點選 ChatGPT 或 Gemini"
                        showShareSheet = true
                    } label: {
                        HStack {
                            Image(systemName: "square.and.arrow.up")
                            Text(payload.hasImage ? "帶截圖＋指令送給 ChatGPT / Gemini" : "透過系統分享送給 AI App")
                                .fontWeight(.semibold)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 13)
                    }
                    .buttonStyle(.borderedProminent)

                    // Direct quick launch row (opens AI app directly with clipboard safety net)
                    VStack(alignment: .leading, spacing: 8) {
                        Text(payload.hasImage ? "或先複製指令並直接開啟 AI App（進入後貼上即可）" : "或一鍵帶入文字開啟 AI")
                            .font(.caption)
                            .foregroundStyle(.secondary)

                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 14) {
                                ForEach(targets, id: \.id) { target in
                                    Button {
                                        let finalPrompt = IosAiDispatcher.composeAndCopyPrompt(
                                            promptBody: promptDraft,
                                            payload: payload
                                        )
                                        toastMessage = "已複製查核指令，正開啟 \(target.displayName)…"
                                        if let url = IosAiDispatcher.buildDirectUrl(
                                            for: target,
                                            finalPrompt: finalPrompt,
                                            hasImage: payload.hasImage
                                        ), let open = onOpenUrl {
                                            open(url)
                                            onDismiss()
                                        } else {
                                            shareItems = IosAiDispatcher.buildShareItems(promptBody: promptDraft, payload: payload)
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
        }
    }

    @ViewBuilder
    private var previewCard: some View {
        HStack(spacing: 12) {
            if let img = payload.image ?? payload.imageUrl.flatMap({ UIImage(contentsOfFile: $0.path) }) {
                Image(uiImage: img)
                    .resizable()
                    .scaledToFill()
                    .frame(width: 58, height: 76)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
            }
            VStack(alignment: .leading, spacing: 4) {
                Text(payload.sourceLabel)
                    .font(.subheadline.weight(.semibold))
                if let text = payload.text, !text.isEmpty {
                    Text(text)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(3)
                } else {
                    Text("僅暫存於本機 App Group 私有快取・無網路權限")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            Spacer(minLength: 0)
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
