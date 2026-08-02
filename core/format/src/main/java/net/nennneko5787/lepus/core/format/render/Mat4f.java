package net.nennneko5787.lepus.core.format.render;

import net.nennneko5787.lepus.core.api.SpecImpl;

/**
 * A 4×4 transform, column-major, with only what a bone hierarchy needs. SC-180 §3.
 *
 * <p><b>Column-major</b> because that is what every graphics API and every matrix class on the
 * Minecraft side expects, so the array crosses the boundary without being transposed — and a
 * transpose that happens in one direction and not the other is a bug that looks like a rotation
 * problem.
 *
 * <p><b>Its own class rather than JOML's</b> because {@code core} is Minecraft-free and JOML arrives
 * with Minecraft. Fifty lines of arithmetic bought against a dependency that would put this whole
 * layer — the one that can be got wrong silently — back on the far side of a client launch.
 *
 * <p>Immutable: every operation returns a new matrix. A bone chain composes by multiplication and
 * nothing accumulates into a shared buffer, so a bone cannot be affected by one traversed before it.
 */
@SpecImpl("SC-180")
public record Mat4f(float[] m) {

    public Mat4f {
        if (m.length != 16) {
            throw new IllegalArgumentException("a 4x4 matrix has 16 elements, not " + m.length);
        }
        m = m.clone();
    }

    public static final Mat4f IDENTITY = new Mat4f(new float[] {
            1, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 1, 0,
            0, 0, 0, 1});

    public static Mat4f translation(float x, float y, float z) {
        return new Mat4f(new float[] {
                1, 0, 0, 0,
                0, 1, 0, 0,
                0, 0, 1, 0,
                x, y, z, 1});
    }

    public static Mat4f scale(float x, float y, float z) {
        return new Mat4f(new float[] {
                x, 0, 0, 0,
                0, y, 0, 0,
                0, 0, z, 0,
                0, 0, 0, 1});
    }

    /** A right-handed rotation about X, in degrees. */
    public static Mat4f rotationX(float degrees) {
        float c = cos(degrees);
        float s = sin(degrees);
        return new Mat4f(new float[] {
                1, 0, 0, 0,
                0, c, s, 0,
                0, -s, c, 0,
                0, 0, 0, 1});
    }

    /** A right-handed rotation about Y, in degrees. */
    public static Mat4f rotationY(float degrees) {
        float c = cos(degrees);
        float s = sin(degrees);
        return new Mat4f(new float[] {
                c, 0, -s, 0,
                0, 1, 0, 0,
                s, 0, c, 0,
                0, 0, 0, 1});
    }

    /** A right-handed rotation about Z, in degrees. */
    public static Mat4f rotationZ(float degrees) {
        float c = cos(degrees);
        float s = sin(degrees);
        return new Mat4f(new float[] {
                c, s, 0, 0,
                -s, c, 0, 0,
                0, 0, 1, 0,
                0, 0, 0, 1});
    }

    /** {@code this * other}: {@code other} applies first, then this. */
    public Mat4f times(Mat4f other) {
        float[] a = this.m;
        float[] b = other.m;
        float[] out = new float[16];
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 4; row++) {
                out[col * 4 + row] =
                        a[row] * b[col * 4]
                                + a[4 + row] * b[col * 4 + 1]
                                + a[8 + row] * b[col * 4 + 2]
                                + a[12 + row] * b[col * 4 + 3];
            }
        }
        return new Mat4f(out);
    }

    /** The point {@code (x, y, z)} transformed. */
    public float[] transform(float x, float y, float z) {
        return new float[] {
                m[0] * x + m[4] * y + m[8] * z + m[12],
                m[1] * x + m[5] * y + m[9] * z + m[13],
                m[2] * x + m[6] * y + m[10] * z + m[14]};
    }

    /** The 16 elements, column-major. A copy: nothing outside can reach in and change one. */
    @Override
    public float[] m() {
        return m.clone();
    }

    private static float cos(float degrees) {
        return (float) Math.cos(Math.toRadians(degrees));
    }

    private static float sin(float degrees) {
        return (float) Math.sin(Math.toRadians(degrees));
    }
}
