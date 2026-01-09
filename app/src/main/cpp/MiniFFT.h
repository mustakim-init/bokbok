#ifndef KISS_FFT_H
#define KISS_FFT_H

#include <stdlib.h>
#include <stdio.h>
#include <math.h>
#include <string.h>
#include <vector>
#include <complex>

// Simple C++ wrapper around a basic Cooley-Tukey FFT
// Adapted for BokBok usage

namespace bokbok {

class MiniFFT {
public:
    MiniFFT(int n) : n_(n) {
        // Pre-compute twiddle factors
        twiddles_.resize(n_/2);
        for (int i=0; i<n_/2; ++i) {
             double angle = -2 * M_PI * i / n_;
             twiddles_[i] = std::polar(1.0, angle);
        }
    }

    void fft(std::vector<std::complex<float>>& data) {
        if (data.size() != n_) data.resize(n_);
        transform(data, false);
    }

    void ifft(std::vector<std::complex<float>>& data) {
         if (data.size() != n_) data.resize(n_);
         transform(data, true);
         // Normalization
         float scale = 1.0f / n_;
         for (auto& x : data) x *= scale;
    }

private:
    int n_;
    std::vector<std::complex<double>> twiddles_; // Use double for precision

    void transform(std::vector<std::complex<float>>& a, bool inverse) {
        // Recursive or iterative? Iterative is safer for stack.
        // Let's implement a simple bit-reversal + butterfly
        
        int n = a.size();
        int j = 0;
        for (int i = 1; i < n; i++) {
            int bit = n >> 1;
            while (j & bit) {
                j ^= bit;
                bit >>= 1;
            }
            j ^= bit;
            if (i < j) std::swap(a[i], a[j]);
        }

        for (int len = 2; len <= n; len <<= 1) {
            double ang = 2 * M_PI / len * (inverse ? 1 : -1);
            std::complex<float> wlen(cos(ang), sin(ang));
            for (int i = 0; i < n; i += len) {
                std::complex<float> w(1);
                for (int j = 0; j < len / 2; j++) {
                    std::complex<float> u = a[i + j];
                    std::complex<float> v = a[i + j + len / 2] * w;
                    a[i + j] = u + v;
                    a[i + j + len / 2] = u - v;
                    w *= wlen;
                }
            }
        }
    }
};

} // namespace bokbok

#endif // KISS_FFT_H
