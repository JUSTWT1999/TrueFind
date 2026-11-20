import CoreGraphics
import Foundation
import UIKit
import TrueFindShared

enum IosImageStitcher {
    private static let analysisWidth = 180
    private static let maxOutputWidth: Int32 = 1080
    private static let maxOutputHeight: Int32 = 6000
    private static let dividerHeight: CGFloat = 4

    static func crop(image: UIImage, normalizedRect: CGRect) -> UIImage {
        let normalized = normalizeOrientation(image)
        guard let cg = normalized.cgImage else { return normalized }
        let w = CGFloat(cg.width)
        let h = CGFloat(cg.height)
        let rect = CGRect(
            x: max(0, min(w - 1, (normalizedRect.origin.x * w).rounded())),
            y: max(0, min(h - 1, (normalizedRect.origin.y * h).rounded())),
            width: max(1, min(w, (normalizedRect.size.width * w).rounded())),
            height: max(1, min(h, (normalizedRect.size.height * h).rounded()))
        )
        guard let cropped = cg.cropping(to: rect) else { return normalized }
        return UIImage(cgImage: cropped, scale: 1.0, orientation: .up)
    }

    static func stitch(frames: [UIImage]) -> UIImage {
        guard !frames.isEmpty else { return UIImage() }
        let normalized = frames.map { normalizeWidth(normalizeOrientation($0)) }
        if normalized.count == 1 { return normalized[0] }

        let baseW = Int(normalized[0].size.width)
        let baseH = Int(normalized[0].size.height)
        guard baseW > 0, baseH > 0 else { return normalized[0] }

        let topIgnoreFull = max(16, Int((Double(baseH) * 0.14).rounded()))
        let bottomIgnoreFull = max(16, Int((Double(baseH) * 0.14).rounded()))

        let analysisH = max(32, Int((Double(baseH) * Double(analysisWidth) / Double(baseW)).rounded()))
        let scaleYToFull = Double(baseH) / Double(analysisH)
        let topIgnoreSmall = Int32((Double(topIgnoreFull) / scaleYToFull).rounded())
        let bottomIgnoreSmall = Int32((Double(bottomIgnoreFull) / scaleYToFull).rounded())

        let grayBuffers = normalized.map { toGrayscaleKotlinByteArray($0, width: analysisWidth, height: analysisH) }

        struct SliceSpec {
            let image: UIImage
            let srcTop: Int
            let srcBottom: Int
            let drawDividerAbove: Bool
        }

        var slices: [SliceSpec] = [
            SliceSpec(image: normalized[0], srcTop: 0, srcBottom: baseH - bottomIgnoreFull, drawDividerAbove: false)
        ]
        var lastKeptIndex = 0

        for i in 1..<normalized.count {
            let match = OverlapFinder.shared.findBestOverlap(
                prevGray: grayBuffers[lastKeptIndex],
                nextGray: grayBuffers[i],
                width: Int32(analysisWidth),
                height: Int32(analysisH),
                topIgnoreRows: topIgnoreSmall,
                bottomIgnoreRows: bottomIgnoreSmall,
                maxMadThreshold: 0.08
            )

            if match.isDuplicateFrame { continue }

            let isLast = (i == normalized.count - 1)
            let srcTop: Int
            if match.isConfident {
                let mapped = Int((Double(match.nextNewContentStartRow) * scaleYToFull).rounded())
                srcTop = max(topIgnoreFull, min(baseH - bottomIgnoreFull, mapped))
            } else {
                srcTop = topIgnoreFull
            }
            let srcBottom = isLast ? baseH : (baseH - bottomIgnoreFull)

            if srcBottom - srcTop > 8 {
                slices.append(
                    SliceSpec(
                        image: normalized[i],
                        srcTop: srcTop,
                        srcBottom: srcBottom,
                        drawDividerAbove: !match.isConfident
                    )
                )
                lastKeptIndex = i
            }
        }

        if slices.count == 1 { return normalized[0] }

        let totalHeight = slices.reduce(0) { acc, s in
            acc + (s.srcBottom - s.srcTop) + (s.drawDividerAbove ? Int(dividerHeight) : 0)
        }

        let bounded = OverlapFinder.shared.computeBoundedDimensions(
            width: Int32(baseW),
            height: Int32(totalHeight),
            maxWidth: maxOutputWidth,
            maxHeight: maxOutputHeight
        )
        let outW = CGFloat(bounded.width)
        let outH = CGFloat(bounded.height)
        let scaleFactor = outW / CGFloat(baseW)

        let format = UIGraphicsImageRendererFormat()
        format.scale = 1.0
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: outW, height: outH), format: format)

        return renderer.image { ctx in
            UIColor.white.setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: outW, height: outH))

            var dstY: CGFloat = 0
            for slice in slices {
                if slice.drawDividerAbove {
                    let h = dividerHeight * scaleFactor
                    UIColor(red: 0.80, green: 0.84, blue: 0.88, alpha: 1.0).setFill()
                    ctx.fill(CGRect(x: 0, y: dstY, width: outW, height: h))
                    dstY += h
                }

                let sliceH = slice.srcBottom - slice.srcTop
                let cropRect = CGRect(x: 0, y: slice.srcTop, width: baseW, height: sliceH)
                if let cg = slice.image.cgImage?.cropping(to: cropRect) {
                    let drawnH = CGFloat(sliceH) * scaleFactor
                    UIImage(cgImage: cg).draw(in: CGRect(x: 0, y: dstY, width: outW, height: drawnH))
                    dstY += drawnH
                }
            }
        }
    }

    private static func normalizeOrientation(_ image: UIImage) -> UIImage {
        if image.imageOrientation == .up, image.scale == 1.0 { return image }
        let pixelSize = CGSize(width: image.size.width * image.scale, height: image.size.height * image.scale)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1.0
        return UIGraphicsImageRenderer(size: pixelSize, format: format).image { _ in
            image.draw(in: CGRect(origin: .zero, size: pixelSize))
        }
    }

    private static func normalizeWidth(_ image: UIImage) -> UIImage {
        let w = Int(image.size.width)
        let h = Int(image.size.height)
        guard w > Int(maxOutputWidth), w > 0 else { return image }
        let targetW = CGFloat(maxOutputWidth)
        let targetH = max(1, (CGFloat(h) * targetW / CGFloat(w)).rounded())
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1.0
        return UIGraphicsImageRenderer(size: CGSize(width: targetW, height: targetH), format: format).image { _ in
            image.draw(in: CGRect(x: 0, y: 0, width: targetW, height: targetH))
        }
    }

    private static func toGrayscaleKotlinByteArray(_ image: UIImage, width: Int, height: Int) -> KotlinByteArray {
        var bytes = [UInt8](repeating: 0, count: width * height)
        let colorSpace = CGColorSpaceCreateDeviceGray()
        if let ctx = CGContext(
            data: &bytes,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: width,
            space: colorSpace,
            bitmapInfo: CGImageAlphaInfo.none.rawValue
        ), let cg = image.cgImage {
            ctx.interpolationQuality = .medium
            ctx.draw(cg, in: CGRect(x: 0, y: 0, width: width, height: height))
        }

        let kArray = KotlinByteArray(size: Int32(bytes.count))
        for i in 0..<bytes.count {
            kArray.set(index: Int32(i), value: Int8(bitPattern: bytes[i]))
        }
        return kArray
    }
}
