{ pkgs ? import <nixpkgs> {} }:

pkgs.mkShell {
  packages = with pkgs; [
    nodejs_24
    bun
  ];

  shellHook = ''
    echo "AMS MoQ Player/Publisher dev environment"
    echo "  npm run dev    — start Vite dev server"
    echo "  npm run build  — production build"
    echo "  bun install    — needed when deps point at the moq/ submodule (bun workspaces)"
  '';
}
