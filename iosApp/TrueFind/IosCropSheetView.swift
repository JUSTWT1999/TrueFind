import SwiftUI
import UIKit

struct IosCropSheetView: View {
    let image: UIImage
    let onCancel: () -> Void
    let onConfirm: (UIImage) -> Void

    @State private var leftFrac: CGFloat = 0.04
    @State private var topFrac: CGFloat = 0.08
    @State private var rightFrac: CGFloat = 0.96
    @State private var bottomFrac: CGFloat = 0.90
    @State private var dragStartFrac: CGFloat? = nil

    var body: some View {
        NavigationStack {
            GeometryReader { geo in
                let fitted = fittedImageRect(imageSize: image.size, containerSize: geo.size)
                ZStack {
                    Color.black.ignoresSafeArea()

                    Image(uiImage: image)
                        .resizable()
                        .scaledToFit()
                        .frame(width: fitted.width, height: fitted.height)
                        .position(x: fitted.midX, y: fitted.midY)

                    // Dim outside crop rect + blue frame + edge handles
                    let l = fitted.minX + fitted.width * leftFrac
                    let t = fitted.minY + fitted.height * topFrac
                    let r = fitted.minX + fitted.width * rightFrac
                    let b = fitted.minY + fitted.height * bottomFrac

                    Path { path in
                        path.addRect(CGRect(origin: .zero, size: geo.size))
                        path.addRect(CGRect(x: l, y: t, width: max(1, r - l), height: max(1, b - t)))
                    }
                    .fill(Color.black.opacity(0.6), style: FillStyle(eoFill: true))
                    .allowsHitTesting(false)

                    Rectangle()
                        .stroke(Color.blue, lineWidth: 2.5)
                        .frame(width: max(1, r - l), height: max(1, b - t))
                        .position(x: (l + r) / 2, y: (t + b) / 2)
                        .allowsHitTesting(false)

                    // Top handle
                    cropHandle(x: (l + r) / 2, y: t, onChanged: { value in
                        let base = dragStartFrac ?? topFrac
                        if dragStartFrac == nil { dragStartFrac = base }
                        let delta = value.translation.height / max(1, fitted.height)
                        topFrac = min(bottomFrac - 0.12, max(0, base + delta))
                    }, onEnded: { dragStartFrac = nil })
                    // Bottom handle
                    cropHandle(x: (l + r) / 2, y: b, onChanged: { value in
                        let base = dragStartFrac ?? bottomFrac
                        if dragStartFrac == nil { dragStartFrac = base }
                        let delta = value.translation.height / max(1, fitted.height)
                        bottomFrac = max(topFrac + 0.12, min(1, base + delta))
                    }, onEnded: { dragStartFrac = nil })
                    // Left handle
                    cropHandle(x: l, y: (t + b) / 2, onChanged: { value in
                        let base = dragStartFrac ?? leftFrac
                        if dragStartFrac == nil { dragStartFrac = base }
                        let delta = value.translation.width / max(1, fitted.width)
                        leftFrac = min(rightFrac - 0.12, max(0, base + delta))
                    }, onEnded: { dragStartFrac = nil })
                    // Right handle
                    cropHandle(x: r, y: (t + b) / 2, onChanged: { value in
                        let base = dragStartFrac ?? rightFrac
                        if dragStartFrac == nil { dragStartFrac = base }
                        let delta = value.translation.width / max(1, fitted.width)
                        rightFrac = max(leftFrac + 0.12, min(1, base + delta))
                    }, onEnded: { dragStartFrac = nil })
                }
            }
            .navigationTitle("拖曳藍點裁切重點")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消", action: onCancel)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("完成") {
                        let normRect = CGRect(
                            x: leftFrac,
                            y: topFrac,
                            width: rightFrac - leftFrac,
                            height: bottomFrac - topFrac
                        )
                        onConfirm(IosImageStitcher.crop(image: image, normalizedRect: normRect))
                    }
                    .fontWeight(.bold)
                }
            }
        }
    }

    private func cropHandle(
        x: CGFloat,
        y: CGFloat,
        onChanged: @escaping (DragGesture.Value) -> Void,
        onEnded: @escaping () -> Void
    ) -> some View {
        Circle()
            .fill(Color.blue)
            .overlay(Circle().stroke(Color.white, lineWidth: 2))
            .frame(width: 22, height: 22)
            .position(x: x, y: y)
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged(onChanged)
                    .onEnded { _ in onEnded() }
            )
    }

    private func fittedImageRect(imageSize: CGSize, containerSize: CGSize) -> CGRect {
        guard imageSize.width > 0, imageSize.height > 0 else { return .zero }
        let pad: CGFloat = 20
        let availW = max(1, containerSize.width - pad * 2)
        let availH = max(1, containerSize.height - pad * 2)
        let scale = min(availW / imageSize.width, availH / imageSize.height)
        let w = imageSize.width * scale
        let h = imageSize.height * scale
        return CGRect(
            x: (containerSize.width - w) / 2,
            y: (containerSize.height - h) / 2,
            width: w,
            height: h
        )
    }
}
