import type { VercelRequest, VercelResponse } from '@vercel/node';

export default async function handler(req: VercelRequest, res: VercelResponse) {
  const lat = Number(req.query.lat);
  const lng = Number(req.query.lng);

  if (Number.isNaN(lat) || Number.isNaN(lng)) {
    return res.status(400).json({ error: 'Valid lat and lng query parameters are required.' });
  }

  try {
    const url = `https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=${encodeURIComponent(
      lat
    )}&lon=${encodeURIComponent(lng)}`;

    const response = await fetch(url, {
      headers: {
        'User-Agent': 'SmartPoseWeb/1.0',
        Accept: 'application/json',
      },
    });

    if (!response.ok) {
      return res.status(200).json({
        displayName: `Lat: ${lat.toFixed(3)}, Lng: ${lng.toFixed(3)}`,
      });
    }

    const data = (await response.json()) as {
      name?: string;
      address?: {
        tourism?: string;
        amenity?: string;
        road?: string;
        neighbourhood?: string;
        suburb?: string;
        city?: string;
        town?: string;
        village?: string;
        state?: string;
        country?: string;
      };
    };

    const addr = data.address || {};
    const primary = data.name || addr.tourism || addr.amenity || addr.road || addr.neighbourhood;
    const locality = addr.city || addr.town || addr.village || addr.suburb || addr.state;
    const parts = [primary, locality, addr.country].filter(
      (part, index, self): part is string => Boolean(part) && self.indexOf(part) === index
    );

    const displayName =
      parts.length > 0 ? parts.join(', ') : `Lat: ${lat.toFixed(3)}, Lng: ${lng.toFixed(3)}`;

    return res.status(200).json({ displayName });
  } catch {
    return res.status(200).json({
      displayName: `Lat: ${lat.toFixed(3)}, Lng: ${lng.toFixed(3)}`,
    });
  }
}
