{ pkgs ? import <nixpkgs> {} }:

pkgs.mkShell {
  packages = with pkgs; [
    nodejs_24
  ];

  shellHook = ''
    echo "AMS MoQ Player/Publisher dev environment"
    echo "  npm run dev    — start Vite dev server"
    echo "  npm run build  — production build"
  '';
}
